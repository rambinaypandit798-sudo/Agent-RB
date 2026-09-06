import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:speech_to_text/speech_to_text.dart';

/* -------------------------------------------------------------------------- */
/* Models                                                                     */
/* -------------------------------------------------------------------------- */

enum AgentModel {
  gemini,
  grok,
  openRouter;

  String get label {
    switch (this) {
      case AgentModel.gemini:
        return 'Gemini';
      case AgentModel.grok:
        return 'Grok';
      case AgentModel.openRouter:
        return 'OpenRouter';
    }
  }

  static AgentModel fromStorage(String? value) {
    return AgentModel.values.firstWhere(
      (model) => model.name == value,
      orElse: () => AgentModel.gemini,
    );
  }
}

class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.text,
    required this.isUser,
    required this.createdAt,
  });

  final String id;
  final String text;
  final bool isUser;
  final DateTime createdAt;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'text': text,
      'isUser': isUser,
      'createdAt': createdAt.toIso8601String(),
    };
  }

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    return ChatMessage(
      id: json['id'] as String,
      text: json['text'] as String,
      isUser: json['isUser'] as bool,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

class ChatTurn {
  const ChatTurn({
    required this.role,
    required this.text,
  });

  final String role;
  final String text;
}

class AiStreamException implements Exception {
  const AiStreamException(
    this.message, {
    this.statusCode,
  });

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

/* -------------------------------------------------------------------------- */
/* Local persistence                                                          */
/* -------------------------------------------------------------------------- */

class LocalStorageService {
  LocalStorageService(this._preferences);

  static const _modelKey = 'rb_ai.selected_model';
  static const _messagesKey = 'rb_ai.chat_messages';
  static const _ttsEnabledKey = 'rb_ai.startup_tts_enabled';

  final SharedPreferences _preferences;

  AgentModel readSelectedModel() {
    return AgentModel.fromStorage(
      _preferences.getString(_modelKey),
    );
  }

  Future<void> writeSelectedModel(AgentModel model) {
    return _preferences.setString(_modelKey, model.name);
  }

  List<ChatMessage> readMessages() {
    final encoded = _preferences.getString(_messagesKey);

    if (encoded == null || encoded.isEmpty) {
      return [];
    }

    try {
      final decoded = jsonDecode(encoded) as List<dynamic>;

      return decoded
          .map(
            (item) => ChatMessage.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: true);
    } on FormatException {
      return [];
    } on TypeError {
      return [];
    }
  }

  Future<void> writeMessages(List<ChatMessage> messages) {
    final encoded = jsonEncode(
      messages.map((message) => message.toJson()).toList(),
    );

    return _preferences.setString(_messagesKey, encoded);
  }

  bool readStartupTtsEnabled() {
    return _preferences.getBool(_ttsEnabledKey) ?? true;
  }

  Future<void> writeStartupTtsEnabled(bool enabled) {
    return _preferences.setBool(_ttsEnabledKey, enabled);
  }

  Future<void> clearMessages() {
    return _preferences.remove(_messagesKey);
  }
}

/* -------------------------------------------------------------------------- */
/* Secure API key storage                                                     */
/* -------------------------------------------------------------------------- */

class ApiKeyStore {
  ApiKeyStore({
    FlutterSecureStorage? storage,
  }) : _storage = storage ?? const FlutterSecureStorage();

  static const _geminiKey = 'rb_ai.gemini_api_key';
  static const _openRouterKey = 'rb_ai.openrouter_api_key';

  final FlutterSecureStorage _storage;

  Future<void> saveGemini(String value) {
    return _storage.write(
      key: _geminiKey,
      value: value.trim(),
    );
  }

  Future<void> saveOpenRouter(String value) {
    return _storage.write(
      key: _openRouterKey,
      value: value.trim(),
    );
  }

  Future<String?> readGemini() {
    return _storage.read(key: _geminiKey);
  }

  Future<String?> readOpenRouter() {
    return _storage.read(key: _openRouterKey);
  }

  Future<bool> hasGemini() async {
    return (await readGemini())?.isNotEmpty ?? false;
  }

  Future<bool> hasOpenRouter() async {
    return (await readOpenRouter())?.isNotEmpty ?? false;
  }

  Future<void> clearGemini() {
    return _storage.delete(key: _geminiKey);
  }

  Future<void> clearOpenRouter() {
    return _storage.delete(key: _openRouterKey);
  }
}

/* -------------------------------------------------------------------------- */
/* Startup text-to-speech                                                     */
/* -------------------------------------------------------------------------- */

class TtsService {
  static const startupMessage =
      'RB.AI all systems online sir. by DevBinayai';

  final FlutterTts _flutterTts = FlutterTts();

  bool _configured = false;

  Future<void> speakStartupMessage() async {
    await _configure();

    try {
      await _flutterTts.awaitSpeakCompletion(true);
      await _flutterTts.speak(startupMessage);
    } finally {
      await _flutterTts.stop();
    }
  }

  Future<void> stop() {
    return _flutterTts.stop();
  }

  Future<void> _configure() async {
    if (_configured) return;

    await _flutterTts.setLanguage('en-US');
    await _flutterTts.setSpeechRate(0.48);
    await _flutterTts.setPitch(0.95);
    await _flutterTts.setVolume(1.0);

    _configured = true;
  }

  void dispose() {
    _flutterTts.stop();
  }
}

/* -------------------------------------------------------------------------- */
/* Gemini and OpenRouter streaming                                            */
/* -------------------------------------------------------------------------- */

class AiStreamService {
  AiStreamService({
    http.Client? client,
  }) : _client = client ?? http.Client();

  final http.Client _client;

  Stream<String> gemini({
    required String apiKey,
    required String model,
    required List<ChatTurn> history,
  }) async* {
    final uri = Uri.parse(
      'https://generativelanguage.googleapis.com/v1beta/models/'
      '$model:streamGenerateContent?alt=sse',
    );

    final request = http.Request('POST', uri)
      ..headers.addAll({
        'Content-Type': 'application/json',
        'x-goog-api-key': apiKey,
      })
      ..body = jsonEncode({
        'contents': history
            .map(
              (turn) => {
                'role': turn.role == 'assistant' ? 'model' : turn.role,
                'parts': [
                  {'text': turn.text},
                ],
              },
            )
            .toList(),
      });

    yield* _readSse(request);
  }

  Stream<String> grok({
    required String apiKey,
    required String model,
    required List<ChatTurn> history,
  }) async* {
    final request = http.Request(
      'POST',
      Uri.parse('https://openrouter.ai/api/v1/chat/completions'),
    )
      ..headers.addAll({
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $apiKey',
        'X-Title': 'RB.AI.Agent v2',
      })
      ..body = jsonEncode({
        'model': model,
        'stream': true,
        'messages': history
            .map(
              (turn) => {
                'role': turn.role,
                'content': turn.text,
              },
            )
            .toList(),
      });

    yield* _readSse(request);
  }

  Stream<String> _readSse(http.Request request) async* {
    final response = await _client.send(request);

    if (response.statusCode < 200 || response.statusCode >= 300) {
      await response.stream.drain();

      throw AiStreamException(
        _safeStatusMessage(response.statusCode),
        statusCode: response.statusCode,
      );
    }

    await for (final line in response.stream
        .transform(utf8.decoder)
        .transform(const LineSplitter())) {
      if (!line.startsWith('data:')) continue;

      final data = line.substring(5).trim();

      if (data.isEmpty || data == '[DONE]') {
        continue;
      }

      final decoded = jsonDecode(data) as Map<String, dynamic>;

      if (decoded['error'] != null) {
        throw const AiStreamException(
          'The selected AI provider returned an error.',
        );
      }

      final openRouterText = _openRouterText(decoded);

      if (openRouterText != null && openRouterText.isNotEmpty) {
        yield openRouterText;
        continue;
      }

      final geminiText = _geminiText(decoded);

      if (geminiText != null && geminiText.isNotEmpty) {
        yield geminiText;
      }
    }
  }

  String? _openRouterText(Map<String, dynamic> event) {
    final choices = event['choices'];

    if (choices is! List ||
        choices.isEmpty ||
        choices.first is! Map) {
      return null;
    }

    final choice = choices.first as Map;
    final delta = choice['delta'];

    if (delta is! Map) return null;

    final content = delta['content'];

    return content is String ? content : null;
  }

  String? _geminiText(Map<String, dynamic> event) {
    final candidates = event['candidates'];

    if (candidates is! List ||
        candidates.isEmpty ||
        candidates.first is! Map) {
      return null;
    }

    final content = (candidates.first as Map)['content'];

    if (content is! Map) return null;

    final parts = content['parts'];

    if (parts is! List ||
        parts.isEmpty ||
        parts.first is! Map) {
      return null;
    }

    final text = (parts.first as Map)['text'];

    return text is String ? text : null;
  }

  String _safeStatusMessage(int statusCode) {
    if (statusCode == 401 || statusCode == 403) {
      return 'The selected API key was rejected. Check it in Settings.';
    }

    if (statusCode == 402) {
      return 'OpenRouter requires available credits.';
    }

    if (statusCode == 429) {
      return 'The provider rate limit was reached. Try again soon.';
    }

    if (statusCode >= 500) {
      return 'The AI provider is temporarily unavailable.';
    }

    return 'The AI request failed. Check your connection and try again.';
  }

  void dispose() {
    _client.close();
  }
}

/* -------------------------------------------------------------------------- */
/* One-shot voice input                                                       */
/* -------------------------------------------------------------------------- */

class VoiceService {
  final SpeechToText _speech = SpeechToText();

  bool _initialized = false;
  bool _stopping = false;
  Completer<String?>? _activeResult;

  Future<bool> initialize() async {
    if (_initialized) return true;

    _initialized = await _speech.initialize(
      onError: (error) {
        final activeResult = _activeResult;

        if (activeResult != null && !activeResult.isCompleted) {
          activeResult.completeError(
            StateError(
              'Speech recognition failed: ${error.errorMsg}',
            ),
          );
        }
      },
    );

    return _initialized;
  }

  Future<String?> listenOnce({
    String? localeId,
  }) async {
    if (_stopping || _speech.isListening) {
      return null;
    }

    if (!await initialize()) {
      throw StateError(
        'Microphone permission or speech service is unavailable.',
      );
    }

    final result = Completer<String?>();
    _activeResult = result;
    _stopping = false;

    await _speech.listen(
      localeId: localeId,
      onResult: (recognition) async {
        if (!recognition.finalResult || _stopping) {
          return;
        }

        _stopping = true;

        // Stop immediately after the first final result.
        await _speech.stop();

        final text = recognition.recognizedWords.trim();

        if (!result.isCompleted) {
          result.complete(text.isEmpty ? null : text);
        }

        _activeResult = null;
        _stopping = false;
      },
    );

    return result.future;
  }

  Future<void> stop() async {
    _stopping = true;
    await _speech.stop();

    final activeResult = _activeResult;

    if (activeResult != null && !activeResult.isCompleted) {
      activeResult.complete(null);
    }

    _activeResult = null;
    _stopping = false;
  }

  Future<void> cancel() async {
    _stopping = true;
    await _speech.cancel();

    final activeResult = _activeResult;

    if (activeResult != null && !activeResult.isCompleted) {
      activeResult.complete(null);
    }

    _activeResult = null;
    _stopping = false;
  }

  bool get isListening => _speech.isListening;

  void dispose() {
    _speech.cancel();
    _activeResult = null;
  }
}

/* -------------------------------------------------------------------------- */
/* Controller                                                                  */
/* -------------------------------------------------------------------------- */

class AgentController extends ChangeNotifier {
  AgentController({
    required this.storage,
    required this.tts,
    required this.aiStream,
    required this.keyStore,
    required this.voice,
  });

  static const geminiModel = 'gemini-2.5-flash';
  static const grokModel = 'x-ai/grok-4.1-fast';

  final LocalStorageService storage;
  final TtsService tts;
  final AiStreamService aiStream;
  final ApiKeyStore keyStore;
  final VoiceService voice;

  AgentModel _selectedModel = AgentModel.gemini;
  List<ChatMessage> _messages = [];

  bool _startupTtsEnabled = true;
  bool _isGenerating = false;
  bool _isListening = false;
  bool _hasGeminiKey = false;
  bool _hasOpenRouterKey = false;

  String _streamingAssistantText = '';
  String? _lastError;

  AgentModel get selectedModel => _selectedModel;

  List<ChatMessage> get messages => List.unmodifiable(_messages);

  bool get startupTtsEnabled => _startupTtsEnabled;
  bool get isGenerating => _isGenerating;
  bool get isListening => _isListening;
  bool get hasGeminiKey => _hasGeminiKey;
  bool get hasOpenRouterKey => _hasOpenRouterKey;

  String get streamingAssistantText => _streamingAssistantText;
  String? get lastError => _lastError;

  Future<void> load() async {
    _selectedModel = storage.readSelectedModel();
    _messages = storage.readMessages();
    _startupTtsEnabled = storage.readStartupTtsEnabled();

    await refreshKeyStatus(notify: false);

    if (_messages.isEmpty) {
      _messages = [
        ChatMessage(
          id: 'boot-message',
          text: 'All systems online. How can I assist you?',
          isUser: false,
          createdAt: DateTime.now(),
        ),
      ];

      await storage.writeMessages(_messages);
    }
  }

  Future<void> refreshKeyStatus({
    bool notify = true,
  }) async {
    _hasGeminiKey = await keyStore.hasGemini();
    _hasOpenRouterKey = await keyStore.hasOpenRouter();

    if (notify) {
      notifyListeners();
    }
  }

  Future<void> saveGeminiKey(String value) async {
    if (value.trim().isEmpty) return;

    await keyStore.saveGemini(value);
    await refreshKeyStatus();
  }

  Future<void> saveOpenRouterKey(String value) async {
    if (value.trim().isEmpty) return;

    await keyStore.saveOpenRouter(value);
    await refreshKeyStatus();
  }

  Future<void> clearGeminiKey() async {
    await keyStore.clearGemini();
    await refreshKeyStatus();
  }

  Future<void> clearOpenRouterKey() async {
    await keyStore.clearOpenRouter();
    await refreshKeyStatus();
  }

  Future<void> announceStartup() async {
    if (_startupTtsEnabled) {
      await tts.speakStartupMessage();
    }
  }

  Future<void> selectModel(AgentModel model) async {
    if (_selectedModel == model || _isGenerating) {
      return;
    }

    _selectedModel = model;
    await storage.writeSelectedModel(model);

    clearError();
    notifyListeners();
  }

  Future<void> setStartupTtsEnabled(bool enabled) async {
    _startupTtsEnabled = enabled;

    await storage.writeStartupTtsEnabled(enabled);

    if (!enabled) {
      await tts.stop();
    }

    notifyListeners();
  }

  Future<void> sendMessage(String rawText) async {
    final text = rawText.trim();

    if (text.isEmpty || _isGenerating) {
      return;
    }

    clearError();

    final key = _selectedModel == AgentModel.gemini
        ? await keyStore.readGemini()
        : await keyStore.readOpenRouter();

    if (key == null || key.trim().isEmpty) {
      _lastError = _selectedModel == AgentModel.gemini
          ? 'Add a Gemini API key in Settings before sending.'
          : 'Add an OpenRouter API key in Settings before sending.';

      notifyListeners();
      return;
    }

    _messages.add(
      ChatMessage(
        id: 'user-${DateTime.now().microsecondsSinceEpoch}',
        text: text,
        isUser: true,
        createdAt: DateTime.now(),
      ),
    );

    await storage.writeMessages(_messages);

    _streamingAssistantText = '';
    _isGenerating = true;

    notifyListeners();

    try {
      final history = _messages
          .map(
            (message) => ChatTurn(
              role: message.isUser ? 'user' : 'assistant',
              text: message.text,
            ),
          )
          .toList();

      final stream = _selectedModel == AgentModel.gemini
          ? aiStream.gemini(
              apiKey: key,
              model: geminiModel,
              history: history,
            )
          : aiStream.grok(
              apiKey: key,
              model: grokModel,
              history: history,
            );

      await for (final chunk in stream) {
        _streamingAssistantText += chunk;
        notifyListeners();
      }

      if (_streamingAssistantText.trim().isEmpty) {
        _lastError = 'The provider returned an empty response.';
      } else {
        _messages.add(
          ChatMessage(
            id: 'assistant-${DateTime.now().microsecondsSinceEpoch}',
            text: _streamingAssistantText,
            isUser: false,
            createdAt: DateTime.now(),
          ),
        );

        await storage.writeMessages(_messages);
      }
    } on AiStreamException catch (error) {
      _lastError = error.message;
    } catch (_) {
      _lastError = 'Could not reach the selected AI provider. Try again.';
    } finally {
      _isGenerating = false;
      notifyListeners();
    }
  }

  Future<String?> listenForVoice() async {
    if (_isListening || _isGenerating) {
      return null;
    }

    _isListening = true;
    clearError();
    notifyListeners();

    try {
      return await voice.listenOnce();
    } on StateError catch (error) {
      _lastError = error.message;
      return null;
    } catch (_) {
      _lastError =
          'Microphone input is unavailable. Check app permissions.';
      return null;
    } finally {
      _isListening = false;
      notifyListeners();
    }
  }

  void clearError() {
    if (_lastError == null) return;

    _lastError = null;
    notifyListeners();
  }

  Future<void> clearChat() async {
    _messages = [
      ChatMessage(
        id: 'boot-message-${DateTime.now().microsecondsSinceEpoch}',
        text: 'All systems online. How can I assist you?',
        isUser: false,
        createdAt: DateTime.now(),
      ),
    ];

    await storage.clearMessages();
    await storage.writeMessages(_messages);

    clearError();
    notifyListeners();
  }

  @override
  void dispose() {
    voice.dispose();
    aiStream.dispose();
    tts.dispose();
    super.dispose();
  }
}

/* -------------------------------------------------------------------------- */
/* Theme                                                                      */
/* -------------------------------------------------------------------------- */

abstract final class AppTheme {
  static const surface = Color(0xFF0E1116);
  static const surfaceSecondary = Color(0xFF161B22);
  static const surfaceTertiary = Color(0xFF21262D);

  static const textPrimary = Color(0xFFE2E8F0);
  static const textSecondary = Color(0xFFC9D1D9);
  static const muted = Color(0xFF8B949E);

  static const cyan = Color(0xFF00F0FF);
  static const cyanSecondary = Color(0xFF38BDF8);
  static const onBrand = Color(0xFF090D12);

  static const success = Color(0xFF3FB950);
  static const warning = Color(0xFFD29922);
  static const error = Color(0xFFF85149);
  static const border = Color(0xFF30363D);

  static ThemeData dark() {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: cyan,
      brightness: Brightness.dark,
    ).copyWith(
      primary: cyan,
      onPrimary: onBrand,
      secondary: cyanSecondary,
      onSecondary: onBrand,
      surface: surface,
      onSurface: textPrimary,
      error: error,
      onError: onBrand,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: surface,
      fontFamily: 'Inter',
      appBarTheme: const AppBarTheme(
        backgroundColor: surface,
        foregroundColor: textPrimary,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: surfaceSecondary,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: border),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceTertiary,
        hintStyle: const TextStyle(color: muted),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: cyan),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: surfaceSecondary,
        indicatorColor: cyan.withAlpha(40),
        labelTextStyle: WidgetStateProperty.all(
          const TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      dividerTheme: const DividerThemeData(
        color: border,
        thickness: 1,
      ),
    );
  }
}

/* -------------------------------------------------------------------------- */
/* Chat widgets                                                               */
/* -------------------------------------------------------------------------- */

class ChatBubble extends StatelessWidget {
  const ChatBubble({
    super.key,
    required this.message,
  });

  final ChatMessage message;

  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser;

    return Column(
      crossAxisAlignment:
          isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
      children: [
        Text(
          isUser ? 'YOU' : 'RB.AI',
          style: const TextStyle(
            color: AppTheme.muted,
            fontSize: 11,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.2,
          ),
        ),
        const SizedBox(height: 6),
        Container(
          constraints: const BoxConstraints(maxWidth: 320),
          padding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 13,
          ),
          decoration: BoxDecoration(
            color: isUser ? AppTheme.cyan : AppTheme.surfaceSecondary,
            borderRadius: BorderRadius.only(
              topLeft: const Radius.circular(12),
              topRight: const Radius.circular(12),
              bottomLeft: Radius.circular(isUser ? 12 : 4),
              bottomRight: Radius.circular(isUser ? 4 : 12),
            ),
            border: isUser
                ? null
                : Border.all(color: AppTheme.border),
          ),
          child: Text(
            message.text,
            style: TextStyle(
              color: isUser ? AppTheme.onBrand : AppTheme.textPrimary,
              fontSize: 15,
              height: 1.35,
            ),
          ),
        ),
      ],
    );
  }
}

class ModelSwitcher extends StatelessWidget {
  const ModelSwitcher({
    super.key,
    required this.selectedModel,
    required this.onChanged,
    this.enabled = true,
  });

  final AgentModel selectedModel;
  final ValueChanged<AgentModel> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return SegmentedButton<AgentModel>(
      segments: AgentModel.values
          .map(
            (model) => ButtonSegment<AgentModel>(
              value: model,
              label: Text(model.label),
            ),
          )
          .toList(),
      selected: {selectedModel},
      onSelectionChanged: enabled
          ? (selection) => onChanged(selection.first)
          : null,
      style: ButtonStyle(
        minimumSize: WidgetStateProperty.all(
          const Size(0, 44),
        ),
        foregroundColor: WidgetStateProperty.resolveWith(
          (states) {
            return states.contains(WidgetState.selected)
                ? AppTheme.cyan
                : AppTheme.textSecondary;
          },
        ),
        side: WidgetStateProperty.all(
          const BorderSide(color: AppTheme.border),
        ),
      ),
    );
  }
}

class ChatComposer extends StatefulWidget {
  const ChatComposer({
    super.key,
    required this.onSend,
    required this.onMicPressed,
    required this.isListening,
    required this.isBusy,
  });

  final ValueChanged<String> onSend;
  final Future<String?> Function() onMicPressed;
  final bool isListening;
  final bool isBusy;

  @override
  State<ChatComposer> createState() => _ChatComposerState();
}

class _ChatComposerState extends State<ChatComposer> {
  final _textController = TextEditingController();

  @override
  void dispose() {
    _textController.dispose();
    super.dispose();
  }

  void _send() {
    final text = _textController.text.trim();

    if (text.isEmpty || widget.isBusy) return;

    widget.onSend(text);
    _textController.clear();
  }

  Future<void> _listen() async {
    if (widget.isBusy || widget.isListening) return;

    final text = await widget.onMicPressed();

    if (!mounted || text == null || text.isEmpty) return;

    _textController
      ..text = text
      ..selection = TextSelection.collapsed(
        offset: text.length,
      );

    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        IconButton(
          key: const Key('chat_mic_button'),
          tooltip: widget.isListening ? 'Listening' : 'Voice input',
          onPressed: widget.isBusy ? null : _listen,
          icon: Icon(
            widget.isListening
                ? Icons.mic_rounded
                : Icons.mic_none_rounded,
          ),
          color: AppTheme.cyanSecondary,
          iconSize: 24,
          constraints: const BoxConstraints(
            minWidth: 48,
            minHeight: 48,
          ),
        ),
        const SizedBox(width: 4),
        Expanded(
          child: TextField(
            controller: _textController,
            enabled: !widget.isBusy,
            textInputAction: TextInputAction.newline,
            minLines: 1,
            maxLines: 4,
            onSubmitted: (_) => _send(),
            decoration: const InputDecoration(
              hintText: 'Command RB.AI...',
              contentPadding: EdgeInsets.symmetric(
                horizontal: 16,
                vertical: 12,
              ),
            ),
          ),
        ),
        const SizedBox(width: 8),
        IconButton.filled(
          tooltip: 'Send message',
          onPressed: widget.isBusy ? null : _send,
          icon: widget.isBusy
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                  ),
                )
              : const Icon(Icons.arrow_upward_rounded),
          color: AppTheme.onBrand,
          style: IconButton.styleFrom(
            backgroundColor: AppTheme.cyan,
            minimumSize: const Size(48, 48),
          ),
        ),
      ],
    );
  }
}

/* -------------------------------------------------------------------------- */
/* Home screen                                                                */
/* -------------------------------------------------------------------------- */

class HomeScreen extends StatelessWidget {
  const HomeScreen({
    super.key,
    required this.controller,
  });

  final AgentController controller;

  @override
  Widget build(BuildContext context) {
    final showStreamingBubble = controller.isGenerating;
    final itemCount =
        controller.messages.length + (showStreamingBubble ? 1 : 0);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'MODEL CHANNEL',
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: AppTheme.muted,
                  letterSpacing: 1.5,
                  fontWeight: FontWeight.w700,
                ),
          ),
          const SizedBox(height: 10),
          ModelSwitcher(
            selectedModel: controller.selectedModel,
            enabled: !controller.isGenerating,
            onChanged: controller.selectModel,
          ),
          if (controller.lastError != null) ...[
            const SizedBox(height: 14),
            ErrorBanner(
              message: controller.lastError!,
              onDismiss: controller.clearError,
            ),
          ],
          const SizedBox(height: 20),
          Expanded(
            child: ListView.separated(
              keyboardDismissBehavior:
                  ScrollViewKeyboardDismissBehavior.onDrag,
              itemCount: itemCount,
              itemBuilder: (context, index) {
                if (index < controller.messages.length) {
                  return ChatBubble(
                    message: controller.messages[index],
                  );
                }

                return ChatBubble(
                  message: ChatMessage(
                    id: 'streaming-assistant',
                    text: controller.streamingAssistantText.isEmpty
                        ? 'Thinking...'
                        : controller.streamingAssistantText,
                    isUser: false,
                    createdAt: DateTime.now(),
                  ),
                );
              },
              separatorBuilder: (_, __) {
                return const SizedBox(height: 18);
              },
            ),
          ),
          const SizedBox(height: 12),
          ChatComposer(
            onSend: controller.sendMessage,
            onMicPressed: controller.listenForVoice,
            isListening: controller.isListening,
            isBusy: controller.isGenerating,
          ),
        ],
      ),
    );
  }
}

class ErrorBanner extends StatelessWidget {
  const ErrorBanner({
    super.key,
    required this.message,
    required this.onDismiss,
  });

  final String message;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: 12,
        vertical: 10,
      ),
      decoration: BoxDecoration(
        color: AppTheme.error.withAlpha(28),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: AppTheme.error.withAlpha(120),
        ),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.warning_amber_rounded,
            color: AppTheme.error,
            size: 20,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: const TextStyle(
                color: AppTheme.textPrimary,
              ),
            ),
          ),
          IconButton(
            tooltip: 'Dismiss error',
            onPressed: onDismiss,
            icon: const Icon(Icons.close_rounded),
            color: AppTheme.muted,
            constraints: const BoxConstraints(
              minWidth: 44,
              minHeight: 44,
            ),
          ),
        ],
      ),
    );
  }
}

/* -------------------------------------------------------------------------- */
/* Live Preview screen                                                        */
/* -------------------------------------------------------------------------- */

class LivePreviewScreen extends StatelessWidget {
  const LivePreviewScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 24),
      children: [
        Text(
          'AGENT TELEMETRY',
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: AppTheme.muted,
                letterSpacing: 1.5,
                fontWeight: FontWeight.w700,
              ),
        ),
        const SizedBox(height: 10),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Row(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: const BoxDecoration(
                    color: AppTheme.success,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'STANDBY MODE',
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      SizedBox(height: 4),
                      Text(
                        'No active agent streams detected.',
                        style: TextStyle(color: AppTheme.muted),
                      ),
                    ],
                  ),
                ),
                const Icon(
                  Icons.wifi_tethering_rounded,
                  color: AppTheme.cyan,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 24),
        const Row(
          children: [
            Metric(label: 'UPTIME', value: '—'),
            SizedBox(width: 12),
            Metric(label: 'LATENCY', value: '—'),
          ],
        ),
        const SizedBox(height: 24),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: const [
                Text(
                  'LIVE LOG',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.1,
                  ),
                ),
                SizedBox(height: 16),
                Text(
                  '[ standby ]  telemetry stream is ready',
                  style: TextStyle(
                    color: AppTheme.muted,
                    fontFamily: 'monospace',
                  ),
                ),
                SizedBox(height: 8),
                Text(
                  '[ phase 2 ]  waiting for task execution',
                  style: TextStyle(
                    color: AppTheme.cyanSecondary,
                    fontFamily: 'monospace',
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class Metric extends StatelessWidget {
  const Metric({
    super.key,
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  color: AppTheme.muted,
                  fontSize: 11,
                  letterSpacing: 1.2,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                value,
                style: const TextStyle(
                  color: AppTheme.cyan,
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/* -------------------------------------------------------------------------- */
/* Settings screen                                                            */
/* -------------------------------------------------------------------------- */

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    super.key,
    required this.controller,
  });

  final AgentController controller;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _geminiKeyController = TextEditingController();
  final _openRouterKeyController = TextEditingController();

  bool _savingGemini = false;
  bool _savingOpenRouter = false;

  AgentController get controller => widget.controller;

  @override
  void dispose() {
    _geminiKeyController.dispose();
    _openRouterKeyController.dispose();
    super.dispose();
  }

  Future<void> _saveGemini() async {
    if (_geminiKeyController.text.trim().isEmpty) return;

    setState(() => _savingGemini = true);

    await controller.saveGeminiKey(_geminiKeyController.text);
    _geminiKeyController.clear();

    if (mounted) {
      setState(() => _savingGemini = false);
    }
  }

  Future<void> _saveOpenRouter() async {
    if (_openRouterKeyController.text.trim().isEmpty) return;

    setState(() => _savingOpenRouter = true);

    await controller.saveOpenRouterKey(
      _openRouterKeyController.text,
    );
    _openRouterKeyController.clear();

    if (mounted) {
      setState(() => _savingOpenRouter = false);
    }
  }

  Future<void> _confirmClearChat() async {
    final shouldClear = await showDialog<bool>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: const Text('Clear local chat?'),
          content: const Text(
            'This removes the saved conversation from this device.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Clear'),
            ),
          ],
        );
      },
    );

    if (shouldClear == true) {
      await controller.clearChat();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 24),
      children: [
        Text(
          'SYSTEM SETTINGS',
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: AppTheme.muted,
                letterSpacing: 1.5,
                fontWeight: FontWeight.w700,
              ),
        ),
        const SizedBox(height: 10),
        Card(
          child: Column(
            children: [
              SwitchListTile.adaptive(
                value: controller.startupTtsEnabled,
                onChanged: (value) {
                  controller.setStartupTtsEnabled(value);
                },
                activeColor: AppTheme.cyan,
                title: const Text('Startup voice announcement'),
                subtitle: const Text(
                  TtsService.startupMessage,
                  style: TextStyle(
                    color: AppTheme.muted,
                    height: 1.35,
                  ),
                ),
              ),
              const Divider(height: 1),
              const ListTile(
                leading: Icon(
                  Icons.record_voice_over_rounded,
                  color: AppTheme.cyanSecondary,
                ),
                title: Text('Voice engine'),
                subtitle: Text(
                  'System default',
                  style: TextStyle(color: AppTheme.muted),
                ),
                trailing: Icon(
                  Icons.chevron_right_rounded,
                  color: AppTheme.muted,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text(
          'API CREDENTIALS',
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: AppTheme.muted,
                letterSpacing: 1.5,
                fontWeight: FontWeight.w700,
              ),
        ),
        const SizedBox(height: 10),
        ApiKeyCard(
          title: 'Gemini',
          subtitle:
              'gemini-2.5-flash · stored securely on this device',
          hintText: 'Paste Gemini API key on device',
          controller: _geminiKeyController,
          isConfigured: controller.hasGeminiKey,
          isSaving: _savingGemini,
          onSave: () => _saveGemini(),
          onClear: controller.hasGeminiKey
              ? () => controller.clearGeminiKey()
              : null,
        ),
        const SizedBox(height: 12),
        ApiKeyCard(
          title: 'Grok via OpenRouter',
          subtitle:
              'x-ai/grok-4.1-fast · stored securely on this device',
          hintText: 'Paste OpenRouter API key on device',
          controller: _openRouterKeyController,
          isConfigured: controller.hasOpenRouterKey,
          isSaving: _savingOpenRouter,
          onSave: () => _saveOpenRouter(),
          onClear: controller.hasOpenRouterKey
              ? () => controller.clearOpenRouterKey()
              : null,
        ),
        const SizedBox(height: 8),
        const Text(
          'Keys are never written to source code or shared preferences. For production, move provider calls behind an authenticated server.',
          style: TextStyle(
            color: AppTheme.muted,
            fontSize: 12,
            height: 1.4,
          ),
        ),
        const SizedBox(height: 24),
        Text(
          'LOCAL DATA',
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
                color: AppTheme.muted,
                letterSpacing: 1.5,
                fontWeight: FontWeight.w700,
              ),
        ),
        const SizedBox(height: 10),
        Card(
          child: ListTile(
            leading: const Icon(
              Icons.delete_sweep_outlined,
              color: AppTheme.warning,
            ),
            title: const Text('Clear saved chat'),
            subtitle: const Text(
              'Model selection and TTS preference stay saved.',
              style: TextStyle(color: AppTheme.muted),
            ),
            trailing: const Icon(
              Icons.chevron_right_rounded,
              color: AppTheme.muted,
            ),
            onTap: _confirmClearChat,
          ),
        ),
        const SizedBox(height: 24),
        const Card(
          child: ListTile(
            leading: Icon(
              Icons.memory_rounded,
              color: AppTheme.cyan,
            ),
            title: Text('RB.AI.Agent v2'),
            subtitle: Text(
              'Phase 2 · live API shell',
              style: TextStyle(color: AppTheme.muted),
            ),
            trailing: Text(
              'v1.1.0',
              style: TextStyle(color: AppTheme.muted),
            ),
          ),
        ),
      ],
    );
  }
}

class ApiKeyCard extends StatelessWidget {
  const ApiKeyCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.hintText,
    required this.controller,
    required this.isConfigured,
    required this.isSaving,
    required this.onSave,
    required this.onClear,
  });

  final String title;
  final String subtitle;
  final String hintText;
  final TextEditingController controller;
  final bool isConfigured;
  final bool isSaving;
  final VoidCallback onSave;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Icon(
                  Icons.lock_outline_rounded,
                  color: AppTheme.cyanSecondary,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: const TextStyle(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        subtitle,
                        style: const TextStyle(
                          color: AppTheme.muted,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                Text(
                  isConfigured ? 'SAVED' : 'NOT SET',
                  style: TextStyle(
                    color: isConfigured
                        ? AppTheme.success
                        : AppTheme.muted,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            TextField(
              controller: controller,
              obscureText: true,
              autocorrect: false,
              enableSuggestions: false,
              decoration: InputDecoration(
                hintText: hintText,
                prefixIcon: const Icon(Icons.key_rounded),
              ),
            ),
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (onClear != null)
                  TextButton(
                    onPressed: onClear,
                    child: const Text('Remove'),
                  ),
                const SizedBox(width: 8),
                FilledButton.tonal(
                  onPressed: isSaving ? null : onSave,
                  child: isSaving
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                          ),
                        )
                      : const Text('Save securely'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/* -------------------------------------------------------------------------- */
/* App shell                                                                  */
/* -------------------------------------------------------------------------- */

class AgentShell extends StatefulWidget {
  const AgentShell({
    super.key,
    required this.controller,
  });

  final AgentController controller;

  @override
  State<AgentShell> createState() => _AgentShellState();
}

class _AgentShellState extends State<AgentShell> {
  int _selectedIndex = 0;

  AgentController get controller => widget.controller;

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      controller.announceStartup();
    });
  }

  void _showActionMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final screens = [
          HomeScreen(controller: controller),
          const LivePreviewScreen(),
          SettingsScreen(controller: controller),
        ];

        return Scaffold(
          appBar: AppBar(
            titleSpacing: 16,
            title: Row(
              children: [
                const Text(
                  'RB AI',
                  style: TextStyle(
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.4,
                  ),
                ),
                const SizedBox(width: 12),
                Container(
                  width: 7,
                  height: 7,
                  decoration: const BoxDecoration(
                    color: AppTheme.success,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                const Text(
                  'ONLINE',
                  style: TextStyle(
                    color: AppTheme.success,
                    fontSize: 11,
                    letterSpacing: 1.1,
                  ),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () {
                  _showActionMessage(
                    'RB.AI command center ready.',
                  );
                },
                child: const Text('RB.AI'),
              ),
              PopupMenuButton<String>(
                tooltip: 'Menu',
                onSelected: (value) {
                  if (value == 'clear') {
                    controller.clearChat();
                  } else {
                    _showActionMessage(
                      'System status: online.',
                    );
                  }
                },
                itemBuilder: (context) {
                  return const [
                    PopupMenuItem(
                      value: 'status',
                      child: Text('System status'),
                    ),
                    PopupMenuItem(
                      value: 'clear',
                      child: Text('Clear chat'),
                    ),
                  ];
                },
              ),
            ],
          ),
          body: IndexedStack(
            index: _selectedIndex,
            children: screens,
          ),
          bottomNavigationBar: NavigationBar(
            selectedIndex: _selectedIndex,
            onDestinationSelected: (index) {
              setState(() => _selectedIndex = index);
            },
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home_rounded),
                label: 'Home',
              ),
              NavigationDestination(
                icon: Icon(Icons.radar_outlined),
                selectedIcon: Icon(Icons.radar_rounded),
                label: 'Live Preview',
              ),
              NavigationDestination(
                icon: Icon(Icons.tune_outlined),
                selectedIcon: Icon(Icons.tune_rounded),
                label: 'Settings',
              ),
            ],
          ),
        );
      },
    );
  }
}

/* -------------------------------------------------------------------------- */
/* App root                                                                   */
/* -------------------------------------------------------------------------- */

class RbAiApp extends StatelessWidget {
  const RbAiApp({
    super.key,
    required this.controller,
  });

  final AgentController controller;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'RB AI',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      home: AgentShell(controller: controller),
    );
  }
}

/* -------------------------------------------------------------------------- */
/* Entrypoint                                                                 */
/* -------------------------------------------------------------------------- */

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final preferences = await SharedPreferences.getInstance();

  final controller = AgentController(
    storage: LocalStorageService(preferences),
    tts: TtsService(),
    aiStream: AiStreamService(),
    keyStore: ApiKeyStore(),
    voice: VoiceService(),
  );

  await controller.load();

  runApp(
    RbAiApp(controller: controller),
  );
}

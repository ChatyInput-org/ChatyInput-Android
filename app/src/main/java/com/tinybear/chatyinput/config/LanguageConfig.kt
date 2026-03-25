package com.tinybear.chatyinput.config

import java.util.Locale

// 支持的语言
enum class AppLanguage(val code: String, val label: String, val nativeLabel: String) {
    AUTO("auto", "Auto (System)", "Auto"),
    ZH_CN("zh-CN", "Simplified Chinese", "简体中文"),
    ZH_TW("zh-TW", "Traditional Chinese", "繁體中文"),
    EN("en", "English", "English"),
    JA("ja", "Japanese", "日本語"),
    KO("ko", "Korean", "한국어");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code == code } ?: AUTO

        // 根据系统语言自动检测
        fun detectSystem(): AppLanguage {
            val locale = Locale.getDefault()
            val lang = locale.language
            val country = locale.country
            return when {
                lang == "zh" && (country == "TW" || country == "HK" || country == "MO") -> ZH_TW
                lang == "zh" -> ZH_CN
                lang == "ja" -> JA
                lang == "ko" -> KO
                lang == "en" -> EN
                else -> EN
            }
        }
    }
}

// 各语言的默认 System Prompt
object LocalizedPrompts {

    fun getDefault(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> PROMPT_ZH_CN
            AppLanguage.ZH_TW -> PROMPT_ZH_TW
            AppLanguage.EN -> PROMPT_EN
            AppLanguage.JA -> PROMPT_JA
            AppLanguage.KO -> PROMPT_KO
            AppLanguage.AUTO -> PROMPT_EN // fallback
        }
    }

    // 编辑指令 Prompt
    fun getEditDefault(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> EDIT_ZH_CN
            AppLanguage.ZH_TW -> EDIT_ZH_TW
            AppLanguage.EN -> EDIT_EN
            AppLanguage.JA -> EDIT_JA
            AppLanguage.KO -> EDIT_KO
            AppLanguage.AUTO -> EDIT_EN
        }
    }

    private const val EDIT_ZH_CN = """你是一个文本编辑助手。用户的主语言是简体中文（请严格使用简体中文）。

用户会通过语音给出编辑指令，告诉你如何修改当前缓冲区中的文字。你的任务是：
1. 理解用户的编辑意图
2. 对缓冲区文字执行相应修改
3. 返回修改后的**完整缓冲区全文**

intent 固定为 "edit"。

严格只返回 JSON：
{"intent": "edit", "result_text": "修改后的完整文本", "explanation": "执行了什么修改"}"""

    private const val EDIT_ZH_TW = """你是一個文本編輯助手。用戶的主語言是繁體中文（請嚴格使用繁體中文）。

用戶會通過語音給出編輯指令，告訴你如何修改當前緩衝區中的文字。你的任務是：
1. 理解用戶的編輯意圖
2. 對緩衝區文字執行相應修改
3. 返回修改後的**完整緩衝區全文**

intent 固定為 "edit"。

嚴格只返回 JSON：
{"intent": "edit", "result_text": "修改後的完整文本", "explanation": "執行了什麼修改"}"""

    private const val EDIT_EN = """You are a text editing assistant. The user's primary language is English.

The user will give editing instructions via voice, telling you how to modify the current buffer text. Your task is:
1. Understand the user's editing intent
2. Apply the modification to the buffer text
3. Return the **complete modified buffer text**

intent is always "edit".

Strictly return JSON only:
{"intent": "edit", "result_text": "complete modified text", "explanation": "what was changed"}"""

    private const val EDIT_JA = """あなたはテキスト編集アシスタントです。ユーザーの主言語は日本語です。

ユーザーは音声で編集指示を出し、現在のバッファテキストをどう修正するか伝えます。あなたのタスクは：
1. ユーザーの編集意図を理解する
2. バッファテキストに修正を適用する
3. 修正後の**バッファ全文**を返す

intent は常に "edit" です。

厳密にJSONのみを返してください：
{"intent": "edit", "result_text": "修正後の完全なテキスト", "explanation": "何を変更したか"}"""

    private const val EDIT_KO = """당신은 텍스트 편집 도우미입니다. 사용자의 주 언어는 한국어입니다.

사용자는 음성으로 편집 지시를 내려 현재 버퍼 텍스트를 어떻게 수정할지 알려줍니다. 당신의 작업은:
1. 사용자의 편집 의도를 이해
2. 버퍼 텍스트에 수정 적용
3. 수정된 **버퍼 전체 텍스트** 반환

intent는 항상 "edit"입니다.

엄격하게 JSON만 반환하세요:
{"intent": "edit", "result_text": "수정된 전체 텍스트", "explanation": "무엇을 변경했는지"}"""

    private const val PROMPT_ZH_CN = """你是一个语音输入助手。用户的主语言是简体中文（请严格使用简体中文，不要使用繁体中文）。用户通过语音逐段输入文字。

每段语音转文字后发给你，判断意图并处理：

1. **content** — 普通内容输入。纠正错别字和语法，result_text 只返回纠正后的**新内容**（不要包含缓冲区已有的文字）。如果用户提供了常用词列表，遇到发音相似的词请优先使用常用词。
2. **edit** — 编辑命令（如"把X改成Y"、"删掉上一句"）。根据命令修改当前缓冲区，result_text 返回修改后的**完整缓冲区全文**。你要很确定用户是真实需要修改他输入的文字才进行修改,需要根据上下文推理.
3. **send** — 发送命令（如"发送"、"确认"、"OK"、"send"）。result_text 留空。你要很确定用户是真实的要发送这段文字了才使用这个命令.

严格只返回 JSON，不要返回任何其他文字，不要用 markdown 代码块包裹：
{"intent": "content", "result_text": "纠正后的新内容", "explanation": "说明"}"""

    private const val PROMPT_ZH_TW = """你是一個語音輸入助手。用戶的主語言是繁體中文（請嚴格使用繁體中文，不要使用簡體中文）。用戶通過語音逐段輸入文字。

每段語音轉文字後發給你，判斷意圖並處理：

1. **content** — 普通內容輸入。糾正錯別字和語法，result_text 只返回糾正後的**新內容**（不要包含緩衝區已有的文字）。如果用戶提供了常用詞列表，遇到發音相似的詞請優先使用常用詞。
2. **edit** — 編輯命令（如"把X改成Y"、"刪掉上一句"）。根據命令修改當前緩衝區，result_text 返回修改後的**完整緩衝區全文**。你要很確定用戶是真實需要修改他輸入的文字才進行修改，需要根據上下文推理。
3. **send** — 發送命令（如"發送"、"確認"、"OK"、"send"）。result_text 留空。你要很確定用戶是真實的要發送這段文字了才使用這個命令。

嚴格只返回 JSON，不要返回任何其他文字，不要用 markdown 代碼塊包裹：
{"intent": "content", "result_text": "糾正後的新內容", "explanation": "說明"}"""

    private const val PROMPT_EN = """You are a voice input assistant. The user's primary language is English. The user inputs text segment by segment via voice.

After each voice segment is transcribed, determine the intent and process:

1. **content** — Normal content input. Correct typos and grammar, result_text should only return the corrected **new content** (do not include existing buffer text). If the user has provided a custom words list, prefer those words when encountering similar-sounding alternatives.
2. **edit** — Edit command (e.g., "change X to Y", "delete the last sentence"). Modify the current buffer based on the command, result_text should return the **complete modified buffer text**. Only edit when you are confident the user genuinely wants to modify their input, infer from context.
3. **send** — Send command (e.g., "send", "confirm", "OK"). Leave result_text empty. Only use this when you are confident the user genuinely wants to send the text.

Strictly return JSON only, no other text, no markdown code blocks:
{"intent": "content", "result_text": "corrected new content", "explanation": "description"}"""

    private const val PROMPT_JA = """あなたは音声入力アシスタントです。ユーザーの主言語は日本語です。ユーザーは音声で文章を段階的に入力します。

各音声セグメントがテキストに変換された後、意図を判断して処理してください：

1. **content** — 通常のコンテンツ入力。誤字や文法を修正し、result_textには修正後の**新しいコンテンツのみ**を返してください（バッファの既存テキストは含めないでください）。ユーザーがカスタム単語リストを提供している場合、発音が似ている単語はカスタム単語を優先してください。
2. **edit** — 編集コマンド（例：「XをYに変えて」「最後の文を削除して」）。コマンドに基づいてバッファを修正し、result_textには修正後の**バッファ全文**を返してください。ユーザーが本当に修正を望んでいると確信できる場合のみ修正し、文脈から推論してください。
3. **send** — 送信コマンド（例：「送信」「確認」「OK」「send」）。result_textは空にしてください。ユーザーが本当にテキストを送信したいと確信できる場合のみ使用してください。

厳密にJSONのみを返してください。他のテキストやmarkdownコードブロックは使用しないでください：
{"intent": "content", "result_text": "修正後の新しいコンテンツ", "explanation": "説明"}"""

    private const val PROMPT_KO = """당신은 음성 입력 도우미입니다. 사용자의 주 언어는 한국어입니다. 사용자는 음성으로 텍스트를 단계적으로 입력합니다.

각 음성 세그먼트가 텍스트로 변환된 후, 의도를 판단하고 처리하세요:

1. **content** — 일반 콘텐츠 입력. 오탈자와 문법을 교정하고, result_text에는 교정된 **새 콘텐츠만** 반환하세요 (버퍼의 기존 텍스트는 포함하지 마세요). 사용자가 자주 쓰는 단어 목록을 제공한 경우, 발음이 비슷한 단어는 자주 쓰는 단어를 우선 사용하세요.
2. **edit** — 편집 명령 (예: "X를 Y로 바꿔", "마지막 문장 삭제해"). 명령에 따라 버퍼를 수정하고, result_text에는 수정된 **버퍼 전체 텍스트**를 반환하세요. 사용자가 정말로 수정을 원한다고 확신할 때만 수정하세요.
3. **send** — 전송 명령 (예: "보내", "확인", "OK", "send"). result_text를 비워두세요. 사용자가 정말로 텍스트를 보내려 한다고 확신할 때만 사용하세요.

엄격하게 JSON만 반환하세요. 다른 텍스트나 markdown 코드 블록은 사용하지 마세요:
{"intent": "content", "result_text": "교정된 새 콘텐츠", "explanation": "설명"}"""
}

package com.tinybear.chatyinput.config

// 内置 Mode 模板的 promptSuffix（附加在全局 prompt 后面）
object ModePrompts {

    // === Business Email ===

    fun getBusinessEmail(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> BUSINESS_ZH_CN
            AppLanguage.ZH_TW -> BUSINESS_ZH_TW
            AppLanguage.EN -> BUSINESS_EN
            AppLanguage.JA -> BUSINESS_JA
            AppLanguage.KO -> BUSINESS_KO
            AppLanguage.FR -> BUSINESS_FR
            AppLanguage.ES -> BUSINESS_ES
            AppLanguage.HI -> BUSINESS_HI
            AppLanguage.AR -> BUSINESS_AR
            AppLanguage.PT -> BUSINESS_PT
            AppLanguage.AUTO -> BUSINESS_EN
        }
    }

    fun getBusinessEmailEdit(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> BUSINESS_EDIT_ZH_CN
            AppLanguage.ZH_TW -> BUSINESS_EDIT_ZH_TW
            AppLanguage.EN -> BUSINESS_EDIT_EN
            AppLanguage.JA -> BUSINESS_EDIT_JA
            AppLanguage.KO -> BUSINESS_EDIT_KO
            AppLanguage.FR -> BUSINESS_EDIT_FR
            AppLanguage.ES -> BUSINESS_EDIT_ES
            AppLanguage.HI -> BUSINESS_EDIT_HI
            AppLanguage.AR -> BUSINESS_EDIT_AR
            AppLanguage.PT -> BUSINESS_EDIT_PT
            AppLanguage.AUTO -> BUSINESS_EDIT_EN
        }
    }

    // === Casual Chat ===

    fun getCasualChat(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> CASUAL_ZH_CN
            AppLanguage.ZH_TW -> CASUAL_ZH_TW
            AppLanguage.EN -> CASUAL_EN
            AppLanguage.JA -> CASUAL_JA
            AppLanguage.KO -> CASUAL_KO
            AppLanguage.FR -> CASUAL_FR
            AppLanguage.ES -> CASUAL_ES
            AppLanguage.HI -> CASUAL_HI
            AppLanguage.AR -> CASUAL_AR
            AppLanguage.PT -> CASUAL_PT
            AppLanguage.AUTO -> CASUAL_EN
        }
    }

    fun getCasualChatEdit(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> CASUAL_EDIT_ZH_CN
            AppLanguage.ZH_TW -> CASUAL_EDIT_ZH_TW
            AppLanguage.EN -> CASUAL_EDIT_EN
            AppLanguage.JA -> CASUAL_EDIT_JA
            AppLanguage.KO -> CASUAL_EDIT_KO
            AppLanguage.FR -> CASUAL_EDIT_FR
            AppLanguage.ES -> CASUAL_EDIT_ES
            AppLanguage.HI -> CASUAL_EDIT_HI
            AppLanguage.AR -> CASUAL_EDIT_AR
            AppLanguage.PT -> CASUAL_EDIT_PT
            AppLanguage.AUTO -> CASUAL_EDIT_EN
        }
    }

    // === Technical Docs ===

    fun getTechnicalDocs(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> TECH_ZH_CN
            AppLanguage.ZH_TW -> TECH_ZH_TW
            AppLanguage.EN -> TECH_EN
            AppLanguage.JA -> TECH_JA
            AppLanguage.KO -> TECH_KO
            AppLanguage.FR -> TECH_FR
            AppLanguage.ES -> TECH_ES
            AppLanguage.HI -> TECH_HI
            AppLanguage.AR -> TECH_AR
            AppLanguage.PT -> TECH_PT
            AppLanguage.AUTO -> TECH_EN
        }
    }

    fun getTechnicalDocsEdit(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> TECH_EDIT_ZH_CN
            AppLanguage.ZH_TW -> TECH_EDIT_ZH_TW
            AppLanguage.EN -> TECH_EDIT_EN
            AppLanguage.JA -> TECH_EDIT_JA
            AppLanguage.KO -> TECH_EDIT_KO
            AppLanguage.FR -> TECH_EDIT_FR
            AppLanguage.ES -> TECH_EDIT_ES
            AppLanguage.HI -> TECH_EDIT_HI
            AppLanguage.AR -> TECH_EDIT_AR
            AppLanguage.PT -> TECH_EDIT_PT
            AppLanguage.AUTO -> TECH_EDIT_EN
        }
    }

    // === Meeting Notes ===

    fun getMeetingNotes(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> MEETING_ZH_CN
            AppLanguage.ZH_TW -> MEETING_ZH_TW
            AppLanguage.EN -> MEETING_EN
            AppLanguage.JA -> MEETING_JA
            AppLanguage.KO -> MEETING_KO
            AppLanguage.FR -> MEETING_FR
            AppLanguage.ES -> MEETING_ES
            AppLanguage.HI -> MEETING_HI
            AppLanguage.AR -> MEETING_AR
            AppLanguage.PT -> MEETING_PT
            AppLanguage.AUTO -> MEETING_EN
        }
    }

    fun getMeetingNotesEdit(language: AppLanguage): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> MEETING_EDIT_ZH_CN
            AppLanguage.ZH_TW -> MEETING_EDIT_ZH_TW
            AppLanguage.EN -> MEETING_EDIT_EN
            AppLanguage.JA -> MEETING_EDIT_JA
            AppLanguage.KO -> MEETING_EDIT_KO
            AppLanguage.FR -> MEETING_EDIT_FR
            AppLanguage.ES -> MEETING_EDIT_ES
            AppLanguage.HI -> MEETING_EDIT_HI
            AppLanguage.AR -> MEETING_EDIT_AR
            AppLanguage.PT -> MEETING_EDIT_PT
            AppLanguage.AUTO -> MEETING_EDIT_EN
        }
    }

    // ========== Business Email Prompts ==========

    private const val BUSINESS_EN = "Additional instructions: Output in formal business email tone. Use complete sentences, professional language, and proper email formatting. Avoid slang, abbreviations, and casual expressions. Maintain a polite and respectful tone."
    private const val BUSINESS_ZH_CN = "附加指令：输出正式商务邮件语气。使用完整句子、专业用语和规范的邮件格式。避免俚语、缩写和口语化表达。保持礼貌、尊重的语气。"
    private const val BUSINESS_ZH_TW = "附加指令：輸出正式商務郵件語氣。使用完整句子、專業用語和規範的郵件格式。避免俚語、縮寫和口語化表達。保持禮貌、尊重的語氣。"
    private const val BUSINESS_JA = "追加指示：フォーマルなビジネスメールのトーンで出力してください。完全な文、専門的な言葉遣い、適切なメール形式を使用してください。スラング、略語、カジュアルな表現は避けてください。丁寧で敬意のあるトーンを維持してください。"
    private const val BUSINESS_KO = "추가 지시: 공식적인 비즈니스 이메일 어조로 출력하세요. 완전한 문장, 전문 용어, 적절한 이메일 형식을 사용하세요. 속어, 약어, 캐주얼한 표현은 피하세요. 공손하고 존중하는 어조를 유지하세요."
    private const val BUSINESS_FR = "Instructions supplémentaires : Produire dans un ton d'e-mail professionnel formel. Utiliser des phrases complètes, un langage professionnel et un formatage d'e-mail approprié. Éviter l'argot, les abréviations et les expressions décontractées. Maintenir un ton poli et respectueux."
    private const val BUSINESS_ES = "Instrucciones adicionales: Producir en tono formal de correo electrónico profesional. Usar oraciones completas, lenguaje profesional y formato de correo apropiado. Evitar jerga, abreviaturas y expresiones informales. Mantener un tono cortés y respetuoso."
    private const val BUSINESS_HI = "अतिरिक्त निर्देश: औपचारिक व्यावसायिक ईमेल टोन में आउटपुट करें। पूर्ण वाक्य, पेशेवर भाषा और उचित ईमेल फ़ॉर्मेटिंग का उपयोग करें। स्लैंग, संक्षिप्त रूप और आकस्मिक अभिव्यक्तियों से बचें। विनम्र और सम्मानजनक टोन बनाए रखें।"
    private const val BUSINESS_AR = "تعليمات إضافية: أنتج بأسلوب بريد إلكتروني رسمي ومهني. استخدم جملاً كاملة ولغة مهنية وتنسيق بريد إلكتروني مناسب. تجنب العامية والاختصارات والتعبيرات غير الرسمية. حافظ على نبرة مهذبة ومحترمة."
    private const val BUSINESS_PT = "Instruções adicionais: Produza em tom formal de e-mail profissional. Use frases completas, linguagem profissional e formatação de e-mail adequada. Evite gírias, abreviações e expressões casuais. Mantenha um tom educado e respeitoso."

    private const val BUSINESS_EDIT_EN = "Additional instructions: When editing, maintain formal business email tone throughout. Ensure professional language is preserved."
    private const val BUSINESS_EDIT_ZH_CN = "附加指令：编辑时保持正式商务邮件语气，确保专业用语不变。"
    private const val BUSINESS_EDIT_ZH_TW = "附加指令：編輯時保持正式商務郵件語氣，確保專業用語不變。"
    private const val BUSINESS_EDIT_JA = "追加指示：編集時もフォーマルなビジネスメールのトーンを維持し、専門的な言葉遣いを保ってください。"
    private const val BUSINESS_EDIT_KO = "추가 지시: 편집 시에도 공식적인 비즈니스 이메일 어조를 유지하고 전문 용어를 보존하세요."
    private const val BUSINESS_EDIT_FR = "Instructions supplémentaires : Lors de l'édition, maintenir le ton formel d'e-mail professionnel. Préserver le langage professionnel."
    private const val BUSINESS_EDIT_ES = "Instrucciones adicionales: Al editar, mantener el tono formal de correo profesional. Preservar el lenguaje profesional."
    private const val BUSINESS_EDIT_HI = "अतिरिक्त निर्देश: संपादन करते समय औपचारिक व्यावसायिक ईमेल टोन बनाए रखें। पेशेवर भाषा को संरक्षित रखें।"
    private const val BUSINESS_EDIT_AR = "تعليمات إضافية: عند التحرير، حافظ على أسلوب البريد الإلكتروني الرسمي والمهني. تأكد من الحفاظ على اللغة المهنية."
    private const val BUSINESS_EDIT_PT = "Instruções adicionais: Ao editar, mantenha o tom formal de e-mail profissional. Preserve a linguagem profissional."

    // ========== Casual Chat Prompts ==========

    private const val CASUAL_EN = "Additional instructions: Output in casual, conversational tone. Keep messages short and natural. Use contractions, informal language, and emoji where appropriate. Write like you're texting a friend."
    private const val CASUAL_ZH_CN = "附加指令：输出口语化、聊天式语气。消息简短自然。可以使用网络用语和 emoji。像和朋友聊天一样写。"
    private const val CASUAL_ZH_TW = "附加指令：輸出口語化、聊天式語氣。消息簡短自然。可以使用網路用語和 emoji。像和朋友聊天一樣寫。"
    private const val CASUAL_JA = "追加指示：カジュアルな会話調で出力してください。メッセージは短く自然に。略語やくだけた表現、絵文字を適宜使用してください。友達にメッセージを送るように書いてください。"
    private const val CASUAL_KO = "추가 지시: 캐주얼하고 대화적인 어조로 출력하세요. 메시지를 짧고 자연스럽게 유지하세요. 줄임말, 비격식 표현, 이모지를 적절히 사용하세요. 친구에게 문자하듯 쓰세요."
    private const val CASUAL_FR = "Instructions supplémentaires : Produire dans un ton décontracté et conversationnel. Garder les messages courts et naturels. Utiliser des contractions, un langage informel et des emoji si approprié. Écrire comme si vous envoyiez un message à un ami."
    private const val CASUAL_ES = "Instrucciones adicionales: Producir en tono casual y conversacional. Mantener los mensajes cortos y naturales. Usar contracciones, lenguaje informal y emoji donde sea apropiado. Escribir como si estuvieras enviando un mensaje a un amigo."
    private const val CASUAL_HI = "अतिरिक्त निर्देश: आकस्मिक, बातचीत के लहजे में आउटपुट करें। संदेश छोटे और स्वाभाविक रखें। संक्षिप्त रूप, अनौपचारिक भाषा और इमोजी का उचित उपयोग करें। ऐसे लिखें जैसे किसी दोस्त को मैसेज कर रहे हों।"
    private const val CASUAL_AR = "تعليمات إضافية: أنتج بأسلوب غير رسمي وحواري. اجعل الرسائل قصيرة وطبيعية. استخدم الاختصارات واللغة غير الرسمية والإيموجي حسب الحاجة. اكتب كأنك ترسل رسالة لصديق."
    private const val CASUAL_PT = "Instruções adicionais: Produza em tom casual e conversacional. Mantenha as mensagens curtas e naturais. Use contrações, linguagem informal e emoji onde apropriado. Escreva como se estivesse mandando mensagem para um amigo."

    private const val CASUAL_EDIT_EN = "Additional instructions: When editing, maintain casual and conversational tone. Keep it natural and friendly."
    private const val CASUAL_EDIT_ZH_CN = "附加指令：编辑时保持口语化聊天语气，自然友好。"
    private const val CASUAL_EDIT_ZH_TW = "附加指令：編輯時保持口語化聊天語氣，自然友好。"
    private const val CASUAL_EDIT_JA = "追加指示：編集時もカジュアルな会話調を維持してください。自然で親しみやすく。"
    private const val CASUAL_EDIT_KO = "추가 지시: 편집 시에도 캐주얼하고 대화적인 어조를 유지하세요. 자연스럽고 친근하게."
    private const val CASUAL_EDIT_FR = "Instructions supplémentaires : Lors de l'édition, maintenir le ton décontracté et conversationnel. Garder un style naturel et amical."
    private const val CASUAL_EDIT_ES = "Instrucciones adicionales: Al editar, mantener el tono casual y conversacional. Mantenerlo natural y amigable."
    private const val CASUAL_EDIT_HI = "अतिरिक्त निर्देश: संपादन करते समय आकस्मिक और बातचीत का लहजा बनाए रखें। स्वाभाविक और मैत्रीपूर्ण रखें।"
    private const val CASUAL_EDIT_AR = "تعليمات إضافية: عند التحرير، حافظ على الأسلوب غير الرسمي والحواري. اجعله طبيعياً وودياً."
    private const val CASUAL_EDIT_PT = "Instruções adicionais: Ao editar, mantenha o tom casual e conversacional. Mantenha natural e amigável."

    // ========== Technical Docs Prompts ==========

    private const val TECH_EN = "Additional instructions: Output in precise technical documentation style. Use exact terminology, structured formatting, and clear definitions. Prefer active voice, be concise and unambiguous. Preserve code identifiers, API names, and technical terms exactly as spoken."
    private const val TECH_ZH_CN = "附加指令：输出精确的技术文档风格。使用准确的技术术语、结构化格式和清晰的定义。偏好主动语态，简洁无歧义。保留代码标识符、API 名称和技术术语的原始拼写。"
    private const val TECH_ZH_TW = "附加指令：輸出精確的技術文檔風格。使用準確的技術術語、結構化格式和清晰的定義。偏好主動語態，簡潔無歧義。保留程式碼標識符、API 名稱和技術術語的原始拼寫。"
    private const val TECH_JA = "追加指示：正確な技術文書スタイルで出力してください。正確な用語、構造化されたフォーマット、明確な定義を使用してください。能動態を好み、簡潔で曖昧さのない表現にしてください。コード識別子、API名、技術用語はそのまま保持してください。"
    private const val TECH_KO = "추가 지시: 정확한 기술 문서 스타일로 출력하세요. 정확한 용어, 구조화된 형식, 명확한 정의를 사용하세요. 능동태를 선호하고, 간결하고 모호하지 않게 작성하세요. 코드 식별자, API 이름, 기술 용어는 그대로 유지하세요."
    private const val TECH_FR = "Instructions supplémentaires : Produire dans un style de documentation technique précis. Utiliser une terminologie exacte, un formatage structuré et des définitions claires. Préférer la voix active, être concis et non ambigu. Préserver les identifiants de code, noms d'API et termes techniques tels quels."
    private const val TECH_ES = "Instrucciones adicionales: Producir en estilo de documentación técnica precisa. Usar terminología exacta, formato estructurado y definiciones claras. Preferir voz activa, ser conciso y sin ambigüedades. Preservar identificadores de código, nombres de API y términos técnicos tal como se dicen."
    private const val TECH_HI = "अतिरिक्त निर्देश: सटीक तकनीकी प्रलेखन शैली में आउटपुट करें। सटीक शब्दावली, संरचित स्वरूपण और स्पष्ट परिभाषाओं का उपयोग करें। सक्रिय आवाज़ पसंद करें, संक्षिप्त और अस्पष्ट न हों। कोड पहचानकर्ता, API नाम और तकनीकी शब्दों को यथावत रखें।"
    private const val TECH_AR = "تعليمات إضافية: أنتج بأسلوب توثيق تقني دقيق. استخدم مصطلحات دقيقة وتنسيقاً منظماً وتعريفات واضحة. فضّل المبني للمعلوم، كن موجزاً وغير غامض. حافظ على معرّفات الكود وأسماء API والمصطلحات التقنية كما هي."
    private const val TECH_PT = "Instruções adicionais: Produza em estilo de documentação técnica precisa. Use terminologia exata, formatação estruturada e definições claras. Prefira voz ativa, seja conciso e sem ambiguidades. Preserve identificadores de código, nomes de API e termos técnicos exatamente como falados."

    private const val TECH_EDIT_EN = "Additional instructions: When editing, maintain precise technical documentation style and preserve all technical terms exactly."
    private const val TECH_EDIT_ZH_CN = "附加指令：编辑时保持精确的技术文档风格，所有技术术语保持原样。"
    private const val TECH_EDIT_ZH_TW = "附加指令：編輯時保持精確的技術文檔風格，所有技術術語保持原樣。"
    private const val TECH_EDIT_JA = "追加指示：編集時も正確な技術文書スタイルを維持し、すべての技術用語をそのまま保持してください。"
    private const val TECH_EDIT_KO = "추가 지시: 편집 시에도 정확한 기술 문서 스타일을 유지하고 모든 기술 용어를 그대로 보존하세요."
    private const val TECH_EDIT_FR = "Instructions supplémentaires : Lors de l'édition, maintenir le style de documentation technique précis et préserver tous les termes techniques."
    private const val TECH_EDIT_ES = "Instrucciones adicionales: Al editar, mantener el estilo de documentación técnica precisa y preservar todos los términos técnicos."
    private const val TECH_EDIT_HI = "अतिरिक्त निर्देश: संपादन करते समय सटीक तकनीकी प्रलेखन शैली बनाए रखें और सभी तकनीकी शब्दों को यथावत रखें।"
    private const val TECH_EDIT_AR = "تعليمات إضافية: عند التحرير، حافظ على أسلوب التوثيق التقني الدقيق واحتفظ بجميع المصطلحات التقنية كما هي."
    private const val TECH_EDIT_PT = "Instruções adicionais: Ao editar, mantenha o estilo de documentação técnica precisa e preserve todos os termos técnicos exatamente."

    // ========== Meeting Notes Prompts ==========

    private const val MEETING_EN = "Additional instructions: Output in meeting notes format. Use bullet points for key items. Mark action items with [ACTION]. Mark decisions with [DECISION]. Keep notes concise and organized by topic. Include names when mentioned."
    private const val MEETING_ZH_CN = "附加指令：输出会议记录格式。使用要点列表列出关键项目。用 [行动项] 标记待办事项。用 [决议] 标记决策。笔记简洁，按主题组织。提到的人名要保留。"
    private const val MEETING_ZH_TW = "附加指令：輸出會議記錄格式。使用要點列表列出關鍵項目。用 [行動項] 標記待辦事項。用 [決議] 標記決策。筆記簡潔，按主題組織。提到的人名要保留。"
    private const val MEETING_JA = "追加指示：議事録形式で出力してください。重要項目は箇条書きにしてください。アクションアイテムは [アクション] で、決定事項は [決定] でマークしてください。ノートは簡潔にトピック別に整理してください。言及された名前は保持してください。"
    private const val MEETING_KO = "추가 지시: 회의록 형식으로 출력하세요. 핵심 항목은 글머리 기호를 사용하세요. 행동 항목은 [액션]으로, 결정 사항은 [결정]으로 표시하세요. 메모는 주제별로 간결하게 정리하세요. 언급된 이름을 포함하세요."
    private const val MEETING_FR = "Instructions supplémentaires : Produire en format de notes de réunion. Utiliser des puces pour les points clés. Marquer les actions à faire avec [ACTION]. Marquer les décisions avec [DÉCISION]. Garder les notes concises et organisées par sujet. Inclure les noms mentionnés."
    private const val MEETING_ES = "Instrucciones adicionales: Producir en formato de notas de reunión. Usar viñetas para elementos clave. Marcar elementos de acción con [ACCIÓN]. Marcar decisiones con [DECISIÓN]. Mantener las notas concisas y organizadas por tema. Incluir nombres mencionados."
    private const val MEETING_HI = "अतिरिक्त निर्देश: मीटिंग नोट्स प्रारूप में आउटपुट करें। प्रमुख बिंदुओं के लिए बुलेट पॉइंट्स का उपयोग करें। कार्य आइटम को [कार्य] से चिह्नित करें। निर्णयों को [निर्णय] से चिह्नित करें। नोट्स संक्षिप्त और विषय-वार व्यवस्थित रखें। उल्लेखित नामों को शामिल करें।"
    private const val MEETING_AR = "تعليمات إضافية: أنتج بتنسيق ملاحظات الاجتماع. استخدم النقاط للعناصر الرئيسية. ضع علامة [إجراء] على بنود العمل. ضع علامة [قرار] على القرارات. اجعل الملاحظات موجزة ومنظمة حسب الموضوع. أدرج الأسماء المذكورة."
    private const val MEETING_PT = "Instruções adicionais: Produza em formato de ata de reunião. Use marcadores para itens-chave. Marque itens de ação com [AÇÃO]. Marque decisões com [DECISÃO]. Mantenha as notas concisas e organizadas por tópico. Inclua nomes mencionados."

    private const val MEETING_EDIT_EN = "Additional instructions: When editing, maintain meeting notes format with bullet points, [ACTION] and [DECISION] markers."
    private const val MEETING_EDIT_ZH_CN = "附加指令：编辑时保持会议记录格式，包括要点列表、[行动项] 和 [决议] 标记。"
    private const val MEETING_EDIT_ZH_TW = "附加指令：編輯時保持會議記錄格式，包括要點列表、[行動項] 和 [決議] 標記。"
    private const val MEETING_EDIT_JA = "追加指示：編集時も議事録形式（箇条書き、[アクション]、[決定] マーカー）を維持してください。"
    private const val MEETING_EDIT_KO = "추가 지시: 편집 시에도 회의록 형식(글머리 기호, [액션], [결정] 마커)을 유지하세요."
    private const val MEETING_EDIT_FR = "Instructions supplémentaires : Lors de l'édition, maintenir le format de notes de réunion avec puces, marqueurs [ACTION] et [DÉCISION]."
    private const val MEETING_EDIT_ES = "Instrucciones adicionales: Al editar, mantener el formato de notas de reunión con viñetas, marcadores [ACCIÓN] y [DECISIÓN]."
    private const val MEETING_EDIT_HI = "अतिरिक्त निर्देश: संपादन करते समय मीटिंग नोट्स प्रारूप बनाए रखें, जिसमें बुलेट पॉइंट्स, [कार्य] और [निर्णय] मार्कर शामिल हों।"
    private const val MEETING_EDIT_AR = "تعليمات إضافية: عند التحرير، حافظ على تنسيق ملاحظات الاجتماع مع النقاط وعلامات [إجراء] و[قرار]."
    private const val MEETING_EDIT_PT = "Instruções adicionais: Ao editar, mantenha o formato de ata de reunião com marcadores, marcadores [AÇÃO] e [DECISÃO]."
}

// LLM Mode 选择上下文提示（附加到 user message 末尾）
object ModeSelectionPrompts {

    fun getContext(language: AppLanguage, modeList: String, currentMode: String, appName: String): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        val tmpl = when (resolved) {
            AppLanguage.ZH_CN -> "---\n可用模式: %s\n当前模式: %s | 应用: %s\n如果你认为应该切换到其他模式，可在 JSON 中加入 \"suggested_mode\":\"mode_id\"。"
            AppLanguage.ZH_TW -> "---\n可用模式: %s\n當前模式: %s | 應用: %s\n如果你認為應該切換到其他模式，可在 JSON 中加入 \"suggested_mode\":\"mode_id\"。"
            AppLanguage.JA -> "---\n利用可能なモード: %s\n現在のモード: %s | アプリ: %s\n別のモードに切り替えるべきと判断した場合、JSONに \"suggested_mode\":\"mode_id\" を含めてください。"
            AppLanguage.KO -> "---\n사용 가능한 모드: %s\n현재 모드: %s | 앱: %s\n다른 모드로 전환해야 한다고 판단되면 JSON에 \"suggested_mode\":\"mode_id\"를 포함하세요."
            AppLanguage.FR -> "---\nModes disponibles : %s\nMode actuel : %s | Application : %s\nSi vous pensez qu'un autre mode serait plus approprié, incluez \"suggested_mode\":\"mode_id\" dans votre JSON."
            AppLanguage.ES -> "---\nModos disponibles: %s\nModo actual: %s | Aplicación: %s\nSi crees que otro modo sería más apropiado, incluye \"suggested_mode\":\"mode_id\" en tu JSON."
            AppLanguage.HI -> "---\nउपलब्ध मोड: %s\nवर्तमान मोड: %s | ऐप: %s\nयदि आपको लगता है कि दूसरा मोड बेहतर होगा, तो JSON में \"suggested_mode\":\"mode_id\" शामिल करें।"
            AppLanguage.AR -> "---\nالأوضاع المتاحة: %s\nالوضع الحالي: %s | التطبيق: %s\nإذا كنت تعتقد أن وضعاً آخر أنسب، أضف \"suggested_mode\":\"mode_id\" في JSON."
            AppLanguage.PT -> "---\nModos disponíveis: %s\nModo atual: %s | Aplicativo: %s\nSe você acha que outro modo seria mais apropriado, inclua \"suggested_mode\":\"mode_id\" no seu JSON."
            else -> "---\nAvailable modes: %s\nCurrent mode: %s | App: %s\nIf you think a different mode would be more appropriate, include \"suggested_mode\":\"mode_id\" in your JSON."
        }
        return String.format(tmpl, modeList, currentMode, appName)
    }

    fun getContextWithConditions(language: AppLanguage, modeDescriptions: String, currentMode: String, appName: String): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        val tmpl = when (resolved) {
            AppLanguage.ZH_CN -> "---\n可用模式（根据条件选择最合适的）:\n%s\n\n当前模式: %s | 应用: %s\n根据当前应用和输入内容，判断是否需要切换模式。如需切换，在 JSON 中加入 \"suggested_mode\":\"mode_id\"。"
            AppLanguage.ZH_TW -> "---\n可用模式（根據條件選擇最合適的）:\n%s\n\n當前模式: %s | 應用: %s\n根據當前應用和輸入內容，判斷是否需要切換模式。如需切換，在 JSON 中加入 \"suggested_mode\":\"mode_id\"。"
            AppLanguage.JA -> "---\n利用可能なモード（条件に基づいて最適なものを選択）:\n%s\n\n現在のモード: %s | アプリ: %s\n現在のアプリと入力内容に基づき、モード切替が必要か判断してください。切替が必要なら \"suggested_mode\":\"mode_id\" をJSONに含めてください。"
            AppLanguage.KO -> "---\n사용 가능한 모드 (조건에 따라 가장 적합한 것을 선택):\n%s\n\n현재 모드: %s | 앱: %s\n현재 앱과 입력 내용을 기반으로 모드 전환이 필요한지 판단하세요. 전환이 필요하면 \"suggested_mode\":\"mode_id\"를 JSON에 포함하세요."
            AppLanguage.FR -> "---\nModes disponibles (choisir le plus approprié selon les conditions) :\n%s\n\nMode actuel : %s | Application : %s\nSelon l'application et le contenu, déterminez si un changement de mode est nécessaire. Si oui, incluez \"suggested_mode\":\"mode_id\" dans votre JSON."
            AppLanguage.ES -> "---\nModos disponibles (elegir el más apropiado según las condiciones):\n%s\n\nModo actual: %s | Aplicación: %s\nSegún la aplicación y el contenido, determine si es necesario cambiar de modo. Si es así, incluya \"suggested_mode\":\"mode_id\" en su JSON."
            AppLanguage.HI -> "---\nउपलब्ध मोड (शर्तों के अनुसार सबसे उपयुक्त चुनें):\n%s\n\nवर्तमान मोड: %s | ऐप: %s\nवर्तमान ऐप और इनपुट के आधार पर, मोड बदलने की आवश्यकता है या नहीं। यदि हाँ, JSON में \"suggested_mode\":\"mode_id\" शामिल करें।"
            AppLanguage.AR -> "---\nالأوضاع المتاحة (اختر الأنسب حسب الشروط):\n%s\n\nالوضع الحالي: %s | التطبيق: %s\nبناءً على التطبيق والمحتوى، حدد ما إذا كان تغيير الوضع ضرورياً. إذا نعم، أضف \"suggested_mode\":\"mode_id\" في JSON."
            AppLanguage.PT -> "---\nModos disponíveis (escolha o mais apropriado segundo as condições):\n%s\n\nModo atual: %s | Aplicativo: %s\nCom base no aplicativo e no conteúdo, determine se uma mudança de modo é necessária. Se sim, inclua \"suggested_mode\":\"mode_id\" no seu JSON."
            else -> "---\nAvailable modes (choose the most appropriate based on conditions):\n%s\n\nCurrent mode: %s | App: %s\nBased on the current app and input content, determine if a mode switch is needed. If so, include \"suggested_mode\":\"mode_id\" in your JSON."
        }
        return String.format(tmpl, modeDescriptions, currentMode, appName)
    }

    fun getLockedContext(language: AppLanguage, currentMode: String, appName: String): String {
        val resolved = if (language == AppLanguage.AUTO) AppLanguage.detectSystem() else language
        return when (resolved) {
            AppLanguage.ZH_CN -> "---\n当前模式: $currentMode（已锁定）| 应用: $appName\n不要建议切换模式。"
            AppLanguage.ZH_TW -> "---\n當前模式: $currentMode（已鎖定）| 應用: $appName\n不要建議切換模式。"
            AppLanguage.JA -> "---\n現在のモード: $currentMode（ロック済み）| アプリ: $appName\nモード切替を提案しないでください。"
            AppLanguage.KO -> "---\n현재 모드: $currentMode（잠금）| 앱: $appName\n모드 전환을 제안하지 마세요."
            AppLanguage.FR -> "---\nMode actuel : $currentMode (verrouillé) | Application : $appName\nNe suggérez pas de changement de mode."
            AppLanguage.ES -> "---\nModo actual: $currentMode (bloqueado) | Aplicación: $appName\nNo sugiera cambios de modo."
            AppLanguage.HI -> "---\nवर्तमान मोड: $currentMode (लॉक) | ऐप: $appName\nमोड बदलने का सुझाव न दें।"
            AppLanguage.AR -> "---\nالوضع الحالي: $currentMode (مقفل) | التطبيق: $appName\nلا تقترح تغيير الوضع."
            AppLanguage.PT -> "---\nModo atual: $currentMode (bloqueado) | Aplicativo: $appName\nNão sugira mudança de modo."
            else -> "---\nCurrent mode: $currentMode (locked) | App: $appName\nDo not suggest mode changes."
        }
    }
}

import { useState, useRef, useEffect } from "react";

// ── 1. 키보드 캡 모양 UI 컴포넌트 (Kbd)
function Kbd({ children }: { children: React.ReactNode }) {
  return (
    <kbd className="mx-0.5 px-2 py-1.5 text-xs font-mono font-bold text-gray-800 bg-white border border-gray-300 rounded-md shadow-[0_2px_0_rgba(0,0,0,0.15),inset_0_-1px_0_rgba(0,0,0,0.15)]">
      {children}
    </kbd>
  );
}

// ── 2. 답변 텍스트 안의 특수 키 조합을 자동으로 감지해 Kbd UI로 변환하는 컴포넌트
function ShortcutParser({ text }: { text: string }) {
  const parts = text.split(
    /(Cmd|Ctrl|Option|Alt|Shift|Down|Up|Tab|Space|F12|\+)/gi,
  );
  return (
    <span className="leading-relaxed whitespace-pre-line">
      {parts.map((part, index) => {
        const isKey = [
          "cmd",
          "ctrl",
          "option",
          "alt",
          "shift",
          "down",
          "up",
          "tab",
          "space",
          "f12",
        ].includes(part.toLowerCase());
        if (isKey) return <Kbd key={index}>{part}</Kbd>;
        if (part === "+")
          return (
            <span key={index} className="mx-1 text-gray-400 font-bold">
              +
            </span>
          );
        return part;
      })}
    </span>
  );
}

interface Message {
  id: number;
  sender: "user" | "bot";
  text: string;
  isTool?: boolean;
}

type TabType = "vscode" | "intellij" | "figma" | "office" | "chrome";

export default function App() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 1,
      sender: "bot",
      text: "안녕하세요! 어떤 툴의 단축키가 궁금하신가요? \n(예시: '인텔리제이 정렬 단축키 알려줘', '엑셀 서식 복사 뭐야?')",
    },
  ]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<TabType>("vscode");

  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isLoading]);

  // ── 3. 백엔드 API 연동 및 자동 탭 전환 로직
  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;

    const userMessage = input.trim();
    setInput("");
    setMessages((prev) => [
      ...prev,
      { id: Date.now(), sender: "user", text: userMessage },
    ]);
    setIsLoading(true);

    // 사용자의 자연어 입력에서 키워드를 파악해 탭 자동 매핑
    if (/피그마|figma/i.test(userMessage)) {
      setActiveTab("figma");
    } else if (/크롬|chrome|시크릿/i.test(userMessage)) {
      setActiveTab("chrome");
    } else if (/코드|vscode|vs code|커서/i.test(userMessage)) {
      setActiveTab("vscode");
    } else if (/인텔리|제이|intellij|idea/i.test(userMessage)) {
      setActiveTab("intellij");
    } else if (
      /엑셀|워드|피피티|ppt|excel|word|오피스|office|서식/i.test(userMessage)
    ) {
      setActiveTab("office");
    }

    try {
      const res = await fetch("http://127.0.0.1:5000/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: userMessage }),
      });

      const data = await res.json();

      setMessages((prev) => [
        ...prev,
        {
          id: Date.now(),
          sender: "bot",
          text: data.answer,
          isTool: data.tool_called,
        },
      ]);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now(),
          sender: "bot",
          text: "서버 오류가 발생했습니다. 파이썬 서버 포트(5000)를 확인해 주세요.",
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  const quickReferences = {
    vscode: [
      {
        name: "멀티 커서 (여러 줄 선택)",
        mac: "Cmd + Option + Down/Up",
        win: "Ctrl + Alt + Down/Up",
      },
      { name: "터미널 토글", mac: "Cmd + `", win: "Ctrl + `" },
      { name: "사이드바 토글", mac: "Cmd + B", win: "Ctrl + B" },
    ],
    intellij: [
      {
        name: "전체 파일 검색 (Double Shift)",
        mac: "Shift + Shift",
        win: "Shift + Shift",
      },
      {
        name: "코드 줄 자동 정렬",
        mac: "Cmd + Option + L",
        win: "Ctrl + Alt + L",
      },
      { name: "스마트 자동 완성", mac: "Ctrl + Space", win: "Ctrl + Space" },
    ],
    figma: [
      {
        name: "오토 레이아웃(Auto Layout)",
        mac: "Shift + A",
        win: "Shift + A",
      },
      { name: "컴포넌트 생성", mac: "Cmd + Option + K", win: "Ctrl + Alt + K" },
      { name: "레이어 이름 일괄 변경", mac: "Cmd + R", win: "Ctrl + R" },
    ],
    office: [
      {
        name: "서식만 복사하기",
        mac: "Cmd + Shift + C",
        win: "Ctrl + Shift + C",
      },
      {
        name: "서식만 붙여넣기",
        mac: "Cmd + Shift + V",
        win: "Ctrl + Shift + V",
      },
      { name: "다른 이름으로 저장", mac: "Cmd + Shift + S", win: "F12" },
    ],
    chrome: [
      {
        name: "시크릿 창 열기",
        mac: "Cmd + Shift + N",
        win: "Ctrl + Shift + N",
      },
      {
        name: "닫은 탭 다시 열기",
        mac: "Cmd + Shift + T",
        win: "Ctrl + Shift + T",
      },
    ],
  };

  return (
    <main className="flex h-screen w-screen bg-slate-50 text-slate-900 overflow-hidden m-0 p-0 font-sans">
      {/* 좌측 패널: 챗봇 창 */}
      <section className="w-1/2 flex flex-col border-r border-slate-200 bg-white h-full">
        <header className="px-6 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-900 text-white shrink-0">
          <h1 className="font-bold text-lg">⌨️ 통합 단축키 마스터 에이전트</h1>
          <span className="text-xs bg-emerald-500 px-2 py-1 rounded-full text-white font-medium">
            Llama 3.1 작동 중
          </span>
        </header>

        <div className="flex-1 overflow-y-auto p-6 space-y-4 bg-slate-50">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex flex-col ${msg.sender === "user" ? "items-end" : "items-start"}`}
            >
              {msg.isTool && (
                <span className="text-[10px] text-indigo-600 font-bold mb-1 bg-indigo-50 px-2 py-0.5 rounded border border-indigo-200">
                  🔍 가이드북 DB 동적 교차 검증 완료
                </span>
              )}
              <div
                className={`max-w-[85%] p-3.5 rounded-2xl shadow-sm text-sm border ${
                  msg.sender === "user"
                    ? "bg-indigo-600 text-white border-indigo-700 rounded-tr-none"
                    : "bg-white text-slate-800 border-slate-200 rounded-tl-none"
                }`}
              >
                <ShortcutParser text={msg.text} />
              </div>
            </div>
          ))}
          {isLoading && (
            <div className="flex flex-col items-start">
              <span className="text-[10px] text-slate-400 font-semibold mb-1 animate-pulse">
                에이전트가 단축키 문서를 분석 중입니다...
              </span>
              <div className="bg-white text-slate-500 border border-slate-200 p-3.5 rounded-2xl rounded-tl-none text-sm shadow-sm">
                Thinking...
              </div>
            </div>
          )}
          <div ref={chatEndRef} />
        </div>

        <form
          onSubmit={handleSend}
          className="p-4 border-t border-slate-200 bg-white flex gap-2 shrink-0"
        >
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="궁금한 단축키나 기능을 질문하세요..."
            className="flex-1 px-4 py-2.5 border border-slate-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-slate-50 text-sm"
            disabled={isLoading}
          />
          <button
            type="submit"
            className="px-5 py-2.5 bg-indigo-600 text-white rounded-xl font-medium text-sm hover:bg-indigo-700 transition shrink-0 disabled:opacity-50"
            disabled={isLoading}
          >
            전송
          </button>
        </form>
      </section>

      {/* 우측 패널: 가이드 보드 */}
      <section className="w-1/2 p-8 flex flex-col h-full bg-slate-50 overflow-y-auto">
        <h2 className="text-xl font-bold text-slate-800 mb-1">
          📚 마스터 단축키 일람표
        </h2>
        <p className="text-sm text-slate-400 mb-6">
          질문 키워드 인식 시 실시간 대시보드 탭 스위칭이 연동됩니다.
        </p>

        <div className="flex border-b border-slate-200 mb-6 gap-1 shrink-0 overflow-x-auto pb-1">
          {(["vscode", "intellij", "figma", "office", "chrome"] as const).map(
            (tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-4 py-2 font-bold text-sm border-b-2 transition capitalize pb-3 shrink-0 ${activeTab === tab ? "border-indigo-600 text-indigo-600" : "border-transparent text-slate-400 hover:text-slate-600"}`}
              >
                {tab === "vscode"
                  ? "VS Code"
                  : tab === "intellij"
                    ? "IntelliJ"
                    : tab === "office"
                      ? "MS Office"
                      : tab}
              </button>
            ),
          )}
        </div>

        <div className="space-y-4 flex-1">
          {quickReferences[activeTab].map((item, idx) => (
            <div
              key={idx}
              className="p-5 bg-white rounded-xl border border-slate-200 shadow-sm hover:border-indigo-300 transition-all"
            >
              <h3 className="font-bold text-slate-800 text-base mb-3">
                📍 {item.name}
              </h3>
              <div className="grid grid-cols-2 gap-4 text-sm bg-slate-50 p-3 rounded-lg">
                <div>
                  <span className="block text-xs font-semibold text-gray-400 mb-1">
                    MacOS
                  </span>
                  <ShortcutParser text={item.mac} />
                </div>
                <div className="border-l border-gray-200 pl-4">
                  <span className="block text-xs font-semibold text-gray-400 mb-1">
                    Windows
                  </span>
                  <ShortcutParser text={item.win} />
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}

import CodeEditor from "@uiw/react-textarea-code-editor";
import { useState, useEffect } from "react";

export function JsonEditor({
  value, onValid, height = 200, placeholder,
}: { value: object; onValid: (v: object) => void; height?: number; placeholder?: string }) {
  const [text, setText] = useState(() => JSON.stringify(value ?? {}, null, 2));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => { setText(JSON.stringify(value ?? {}, null, 2)); }, [value]);

  return (
    <div>
      <CodeEditor
        value={text}
        language="json"
        placeholder={placeholder}
        onChange={(e) => {
          const v = e.target.value;
          setText(v);
          try { const parsed = JSON.parse(v); setError(null); onValid(parsed); }
          catch (err) { setError((err as Error).message); }
        }}
        style={{ fontSize: 13, fontFamily: "ui-monospace, monospace", minHeight: height,
                 background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 6 }}
      />
      {error && <p className="text-xs text-red-600 mt-1">JSON: {error}</p>}
    </div>
  );
}

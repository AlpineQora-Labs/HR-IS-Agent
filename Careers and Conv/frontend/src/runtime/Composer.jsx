import { useState } from 'react';

// Renders the right input for the awaiting node (§7: quick replies preferred; number/text when free
// input is required). Disabled while the bot is "thinking" or once the session ends.
export default function Composer({ awaiting, disabled, onSend }) {
  const [text, setText] = useState('');

  if (!awaiting) return null;

  const quickReplies = awaiting.quickReplies || [];
  const isNumber = awaiting.answerType === 'NUMBER';

  if (quickReplies.length > 0) {
    return (
      <div className="composer">
        <div className="quick">
          {quickReplies.map((opt) => (
            <button key={opt} disabled={disabled} onClick={() => onSend(opt)}>
              {opt}
            </button>
          ))}
        </div>
      </div>
    );
  }

  const submit = () => {
    const v = text.trim();
    if (!v) return;
    onSend(v);
    setText('');
  };

  return (
    <div className="composer">
      <div className="entry">
        <input
          type={isNumber ? 'number' : 'text'}
          value={text}
          disabled={disabled}
          placeholder={awaiting.placeholder || (isNumber ? 'Enter a number…' : 'Type your answer…')}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') submit(); }}
        />
        <button disabled={disabled || !text.trim()} onClick={submit}>Send</button>
      </div>
    </div>
  );
}

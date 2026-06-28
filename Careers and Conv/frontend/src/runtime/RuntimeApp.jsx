import { useSession } from '../store/useSession.js';
import RolePicker from './RolePicker.jsx';
import ChatWindow from './ChatWindow.jsx';
import DecisionTrail from './DecisionTrail.jsx';

// Candidate-facing runtime shell: landing (role picker / job search) and the chat + decision trail.
export default function RuntimeApp() {
  const { phase, mode, trail } = useSession();
  const showTrail = mode === 'screening' || trail.length > 0;

  return (
    <div className="app">
      <header className="app-head">
        <div className="mark">BofA</div>
        <div>
          <h1>Bank of America Careers</h1>
          <div className="sub">Conversational screening · explainable &amp; human-reviewed</div>
        </div>
      </header>

      {phase === 'landing' ? (
        <RolePicker />
      ) : (
        <div className={`runtime ${showTrail ? '' : 'solo'}`}>
          <ChatWindow />
          {showTrail && <DecisionTrail trail={trail} />}
        </div>
      )}
    </div>
  );
}

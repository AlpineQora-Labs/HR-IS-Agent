import { Routes, Route } from 'react-router-dom';
import RuntimeApp from './runtime/RuntimeApp.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RuntimeApp />} />
    </Routes>
  );
}

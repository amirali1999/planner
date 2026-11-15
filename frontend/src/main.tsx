import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import '@fontsource-variable/vazirmatn';
import { applyTheme, storedTheme } from './shared/theme';
import './styles.css';

applyTheme(storedTheme());

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);

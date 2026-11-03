import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { clearLegacyLaboratoryData } from './storage/legacy-laboratories';

clearLegacyLaboratoryData();

const root = document.getElementById('root');
if (!root) throw new Error('Elemento raiz da aplicação não encontrado.');

createRoot(root).render(<App/>);

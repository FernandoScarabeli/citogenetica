export type Protocol = 'meiose' | 'mitose';
export type Entry = { id: string; phase: string; status: 'normal' | 'anormal'; type: string; createdAt: string; editedAt?: string };
export type Slide = { labId?: string; id: string; name: string; protocol: Protocol; species: string; treatment: string; notes: string; goal?: number | null; createdAt: string; entries: Entry[] };
export type State = { version: 1; slides: Slide[]; customTypes: Record<string, string[]> };
const UUID_PATTERN = /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i;

export function slidesForLaboratory(slides: Slide[], laboratoryId: string | null): Slide[] {
  if (laboratoryId) return slides.filter(slide => slide.labId === laboratoryId);
  return slides.filter(slide => !slide.labId || !UUID_PATTERN.test(slide.labId));
}

export const protocols: Record<Protocol, { name: string; phases: { name: string; types: string[] }[] }> = {
  meiose: { name: 'Meiose', phases: [
    {name:'Leptóteno/Zigóteno', types:[]}, {name:'Paquíteno',types:[]}, {name:'Diplóteno/Diacinese',types:['Univalente','Trivalente','Tetravalente']},
    {name:'Metáfase I',types:['Cromossomos não orientados']}, {name:'Anáfase I',types:['Cromossomos retardatários','Segregação de cromátides']}, {name:'Telófase I',types:['Micronúcleo','Fuso tripolar']},
    {name:'Prófase II',types:['Micronúcleo','Núcleos assincrônicos']}, {name:'Metáfase II',types:['Cromossomos não orientados','Assincronia']}, {name:'Anáfase II',types:['Cromossomos retardatários','Assincronia']}, {name:'Telófase II',types:['Micronúcleo','Ponte']}, {name:'Tétrade',types:['Micronúcleo','Tríade','Díade']}
  ] },
  mitose: { name: 'Mitose', phases: [
    {name:'Interfase',types:['Núcleos apoptóticos (condensados)']}, {name:'Prófase',types:['Stickiness','Cromossomos ou fragmentos desprendidos','Núcleo com brotações','Micronúcleo']}, {name:'Metáfase',types:['Cromossomos não orientados','Metáfase colchicínica','Stickiness']}, {name:'Anáfase',types:['Cromossomos atrasados','Fuso anormal','Stickiness','Ponte','Ponte com fragmento']}, {name:'Telófase',types:['Fuso anormal','Ponte','Ponte com fragmento','Micronúcleo: 1','Micronúcleo: 2','Micronúcleo: 3','Micronúcleo: mais de 3']}
  ] }
};
export const emptyState = (): State => ({version:1, slides:[], customTypes:{}});
export const keyFor = (protocol: Protocol, phase: string) => `${protocol}:${phase}`;
export const normalize = (value: string) => value.trim().replace(/\s+/g,' ').toLocaleLowerCase('pt-BR');
export function summary(slide: Slide) {
  const phases = protocols[slide.protocol].phases.map(phase => {
    const entries = slide.entries.filter(entry => entry.phase === phase.name);
    const normal = entries.filter(entry => entry.status === 'normal').length;
    const abnormal = entries.length - normal;
    const types = entries.filter(entry => entry.status === 'anormal').reduce<Record<string,number>>((acc, entry) => { acc[entry.type] = (acc[entry.type] || 0) + 1; return acc; }, {});
    return {...phase, normal, abnormal, total:entries.length, types};
  });
  const total = slide.entries.length;
  const dividing = phases.filter(p => p.name !== 'Interfase').reduce((sum,p) => sum + p.total, 0);
  return {phases, total, abnormal: phases.reduce((sum,p)=>sum+p.abnormal,0), im: total ? dividing / total * 100 : 0};
}
export const pct = (num: number, den: number) => `${(den ? num / den * 100 : 0).toFixed(2).replace('.', ',')}%`;
export function isState(value: unknown): value is State {
  if (!value || typeof value !== 'object') return false;
  const v = value as State;
  return v.version === 1 && Array.isArray(v.slides) && !!v.customTypes && typeof v.customTypes === 'object' && v.slides.every(s => (s.labId === undefined || typeof s.labId === 'string') && typeof s.id === 'string' && typeof s.name === 'string' && (s.protocol === 'mitose' || s.protocol === 'meiose') && typeof s.species === 'string' && typeof s.treatment === 'string' && typeof s.notes === 'string' && (s.goal === undefined || s.goal === null || (Number.isSafeInteger(s.goal) && s.goal > 0)) && typeof s.createdAt === 'string' && Array.isArray(s.entries) && s.entries.every(e => typeof e.id === 'string' && typeof e.phase === 'string' && protocols[s.protocol].phases.some(p => p.name === e.phase) && (e.status === 'normal' || e.status === 'anormal') && typeof e.type === 'string' && typeof e.createdAt === 'string')) && Object.values(v.customTypes).every(types => Array.isArray(types) && types.every(t => typeof t === 'string'));
}

// Lâminas antigas de mitose preservam a meta anteriormente exibida.
export const slideGoal = (slide: Slide): number | null => slide.goal === undefined ? (slide.protocol === 'mitose' ? 1000 : null) : slide.goal;
export function removeMatching(entries: Entry[], phase: string, status: Entry['status'], type: string, quantity: number): Entry[] {
 if (!Number.isSafeInteger(quantity) || quantity < 1) throw new Error('Quantidade inválida');
 const candidates = entries.filter(e => e.phase === phase && e.status === status && (status === 'normal' || e.type === type));
 if (quantity > candidates.length) throw new Error('Quantidade maior que os registros disponíveis');
 const ids = new Set(candidates.slice(-quantity).map(e => e.id));
 return entries.filter(e => !ids.has(e.id));
}

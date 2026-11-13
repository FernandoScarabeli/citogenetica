import type { PapelLaboratorio } from './api/contracts/laboratorio';
import { PAPEL_LABEL } from './api/contracts/laboratorio';

function hasRole(papeis: PapelLaboratorio[], papel: PapelLaboratorio): boolean {
  return papeis.includes(papel);
}

export function RoleFields({ value, onChange }: { value: PapelLaboratorio[]; onChange: (value: PapelLaboratorio[]) => void }) {
  const roles: PapelLaboratorio[] = ['MEMBRO', 'ADMIN', 'ORIENTADOR'];
  return <fieldset className="role-fields"><legend>Papéis no laboratório</legend>
    {roles.map(role => <label className="role-field" key={role}>
      <input type="checkbox" checked={hasRole(value, role)} disabled={role === 'MEMBRO'} onChange={event => {
        if (role === 'MEMBRO') return;
        onChange(event.target.checked
          ? [...new Set<PapelLaboratorio>([...value, role, 'MEMBRO'])]
          : value.filter(item => item !== role));
      }} />
      <span><strong>{PAPEL_LABEL[role]}</strong>{role === 'MEMBRO' && <small>Obrigatório para toda participação.</small>}</span>
    </label>)}
  </fieldset>;
}

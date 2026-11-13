import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ChevronLeft, Eye, EyeOff, UserRound } from 'lucide-react';
import './profile.css';
import { ApiError, NetworkError } from '../api/errors';
import { fotoPerfilUrl, usuarioApi } from '../api/usuario-api';
import type { UsuarioResponseDTO } from '../api/contracts/usuario';
import { passwordPolicyError, validateResendEmail } from './registration-validation';
import { retryCountdownMessage, useRetryCountdown } from './useRetryCountdown';

type Feedback = { kind: 'success' | 'error'; message: string };
type PasswordField = 'currentPassword' | 'newPassword' | 'confirmation';
type PasswordErrors = Partial<Record<PasswordField, string>>;
type EmailField = 'newEmail' | 'currentPassword';
type EmailErrors = Partial<Record<EmailField, string>>;

interface ProfileSettingsProps {
  user: UsuarioResponseDTO;
  onBack: () => void;
  onUserUpdate: (user: UsuarioResponseDTO) => void;
}

function requestErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError || error instanceof NetworkError) return error.message || fallback;
  return fallback;
}

/** Campo de senha com o botão de mostrar/ocultar (recomendação de UX para formulários de senha). */
function PasswordInput({ inputRef, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { inputRef: React.RefObject<HTMLInputElement | null>; id: string }) {
  const [visible, setVisible] = useState(false);
  return <div className="profile-password">
    <input ref={inputRef} {...props} type={visible ? 'text' : 'password'}/>
    <button className="profile-password-toggle" type="button" aria-label={visible ? 'Ocultar senha' : 'Mostrar senha'} aria-pressed={visible} aria-controls={props.id} onClick={() => setVisible(value => !value)}>
      {visible ? <EyeOff size={18} aria-hidden="true"/> : <Eye size={18} aria-hidden="true"/>}
    </button>
  </div>;
}

function FeedbackMessage({ feedback }: { feedback: Feedback | null }) {
  if (!feedback) return null;
  return <p className={`profile-feedback${feedback.kind === 'error' ? ' error' : ''}`} role={feedback.kind === 'error' ? 'alert' : 'status'}>
    {feedback.message}
  </p>;
}

export function ProfileSettings({ user, onBack, onUserUpdate }: ProfileSettingsProps) {
  const [emailOpen, setEmailOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [name, setName] = useState(user.nome);
  const [nameError, setNameError] = useState<string | null>(null);
  const [nameFeedback, setNameFeedback] = useState<Feedback | null>(null);
  const [savingName, setSavingName] = useState(false);
  const [photoFeedback, setPhotoFeedback] = useState<Feedback | null>(null);
  const [savingPhoto, setSavingPhoto] = useState(false);
  const photo = fotoPerfilUrl(user);
  const [password, setPassword] = useState({ current: '', next: '', confirmation: '' });
  const [passwordErrors, setPasswordErrors] = useState<PasswordErrors>({});
  const [passwordFeedback, setPasswordFeedback] = useState<Feedback | null>(null);
  const [savingPassword, setSavingPassword] = useState(false);
  const [email, setEmail] = useState({ next: '', currentPassword: '' });
  const [emailErrors, setEmailErrors] = useState<EmailErrors>({});
  const [emailFeedback, setEmailFeedback] = useState<Feedback | null>(null);
  const [emailRateLimited, setEmailRateLimited] = useState(false);
  const [emailBusy, setEmailBusy] = useState(false);
  const countdown = useRetryCountdown();
  const fileRef = useRef<HTMLInputElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const currentPasswordRef = useRef<HTMLInputElement>(null);
  const newPasswordRef = useRef<HTMLInputElement>(null);
  const confirmationRef = useRef<HTMLInputElement>(null);
  const newEmailRef = useRef<HTMLInputElement>(null);
  const emailPasswordRef = useRef<HTMLInputElement>(null);

  useEffect(() => setName(user.nome), [user.nome]);

  function setPasswordField(field: keyof typeof password, value: string) {
    setPassword((current) => ({ ...current, [field]: value }));
    const errorField: PasswordField = field === 'current' ? 'currentPassword' : field === 'next' ? 'newPassword' : 'confirmation';
    setPasswordErrors((current) => ({ ...current, [errorField]: undefined }));
    setPasswordFeedback(null);
  }

  function setEmailField(field: keyof typeof email, value: string) {
    setEmail((current) => ({ ...current, [field]: value }));
    const errorField: EmailField = field === 'next' ? 'newEmail' : 'currentPassword';
    setEmailErrors((current) => ({ ...current, [errorField]: undefined }));
    if (!emailRateLimited) setEmailFeedback(null);
  }

  async function selectPhoto(file: File | undefined) {
    if (!file || savingPhoto) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024) {
      setPhotoFeedback({ kind: 'error', message: 'Escolha uma imagem JPG, PNG ou WebP de até 2 MB.' });
      return;
    }
    setSavingPhoto(true);
    setPhotoFeedback(null);
    try {
      onUserUpdate(await usuarioApi.updateMyPhoto(file));
      setPhotoFeedback({ kind: 'success', message: 'Foto atualizada.' });
    } catch (error) {
      setPhotoFeedback({ kind: 'error', message: requestErrorMessage(error, 'Não foi possível salvar a foto. Tente novamente.') });
    } finally {
      setSavingPhoto(false);
    }
  }

  async function saveName(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (savingName) return;
    if (!name.trim()) {
      setNameError('Informe seu nome.');
      setNameFeedback(null);
      nameRef.current?.focus();
      return;
    }

    setSavingName(true);
    setNameError(null);
    setNameFeedback(null);
    try {
      const updatedUser = await usuarioApi.updateMyProfile({ nome: name.trim() });
      onUserUpdate(updatedUser);
      setName(updatedUser.nome);
      setNameFeedback({ kind: 'success', message: 'Nome atualizado.' });
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.nome?.[0]) {
        setNameError(error.fieldErrors.nome[0]);
        nameRef.current?.focus();
      } else {
        setNameFeedback({ kind: 'error', message: requestErrorMessage(error, 'Não foi possível salvar o nome. Tente novamente.') });
      }
    } finally {
      setSavingName(false);
    }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (savingPassword) return;

    const errors: PasswordErrors = {};
    if (!password.current) errors.currentPassword = 'Informe sua senha atual.';
    const policyError = passwordPolicyError(password.next);
    if (policyError) errors.newPassword = policyError;
    else if (password.next === password.current) errors.newPassword = 'A nova senha deve ser diferente da atual.';
    if (!password.confirmation) errors.confirmation = 'Confirme sua nova senha.';
    else if (password.next !== password.confirmation) errors.confirmation = 'As senhas não coincidem.';

    if (Object.keys(errors).length > 0) {
      setPasswordErrors(errors);
      setPasswordFeedback(null);
      if (errors.currentPassword) currentPasswordRef.current?.focus();
      else if (errors.newPassword) newPasswordRef.current?.focus();
      else confirmationRef.current?.focus();
      return;
    }

    setSavingPassword(true);
    setPasswordErrors({});
    setPasswordFeedback(null);
    try {
      await usuarioApi.updateMyPassword({ senhaAtual: password.current, novaSenha: password.next });
      setPassword({ current: '', next: '', confirmation: '' });
      setPasswordFeedback({
        kind: 'success',
        message: 'Senha alterada. As outras sessões desta conta foram encerradas.',
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        if (error.message === 'A nova senha deve ser diferente da atual.') {
          setPasswordErrors({ newPassword: error.message });
          newPasswordRef.current?.focus();
        } else {
          setPasswordErrors({ currentPassword: 'A senha atual está incorreta.' });
          currentPasswordRef.current?.focus();
        }
      } else if (error instanceof ApiError && error.status === 400) {
        const serverFields = error.fieldErrors;
        const nextErrors: PasswordErrors = {
          ...(serverFields.senhaAtual?.[0] ? { currentPassword: serverFields.senhaAtual[0] } : {}),
          ...(serverFields.novaSenha?.[0] ? { newPassword: serverFields.novaSenha[0] } : {}),
        };
        if (Object.keys(nextErrors).length > 0) {
          setPasswordErrors(nextErrors);
          if (nextErrors.currentPassword) currentPasswordRef.current?.focus();
          else newPasswordRef.current?.focus();
        } else {
          setPasswordErrors({ newPassword: error.message || 'A nova senha não foi aceita.' });
          newPasswordRef.current?.focus();
        }
      } else {
        setPasswordFeedback({ kind: 'error', message: requestErrorMessage(error, 'Não foi possível alterar a senha. Tente novamente.') });
      }
    } finally {
      setSavingPassword(false);
    }
  }

  async function requestEmailChange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (emailBusy || countdown.isWaiting) return;

    const errors: EmailErrors = {};
    const emailError = validateResendEmail(email.next);
    if (emailError) errors.newEmail = emailError;
    if (!email.currentPassword) errors.currentPassword = 'Informe sua senha atual.';
    if (Object.keys(errors).length > 0) {
      setEmailErrors(errors);
      setEmailFeedback(null);
      if (errors.newEmail) newEmailRef.current?.focus();
      else emailPasswordRef.current?.focus();
      return;
    }

    setEmailBusy(true);
    setEmailErrors({});
    setEmailFeedback(null);
    setEmailRateLimited(false);
    try {
      await usuarioApi.requestEmailChange({ novoEmail: email.next.trim(), senhaAtual: email.currentPassword });
      setEmail((current) => ({ ...current, currentPassword: '' }));
      setEmailFeedback({
        kind: 'success',
        message: 'Enviamos um link para o novo endereço. O e-mail atual continua valendo até você confirmar.',
      });
      countdown.clear();
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setEmailErrors({ currentPassword: 'A senha atual está incorreta.' });
        emailPasswordRef.current?.focus();
      } else if (error instanceof ApiError && error.status === 429) {
        setEmailRateLimited(true);
        countdown.start(error.retryAfterSeconds);
      } else if (error instanceof ApiError && error.status === 400) {
        const serverFields = error.fieldErrors;
        const nextErrors: EmailErrors = {
          ...(serverFields.novoEmail?.[0] ? { newEmail: serverFields.novoEmail[0] } : {}),
          ...(serverFields.senhaAtual?.[0] ? { currentPassword: serverFields.senhaAtual[0] } : {}),
        };
        if (Object.keys(nextErrors).length > 0) {
          setEmailErrors(nextErrors);
          if (nextErrors.newEmail) newEmailRef.current?.focus();
          else emailPasswordRef.current?.focus();
        } else {
          setEmailErrors({ newEmail: error.message || 'Confira o endereço informado.' });
          newEmailRef.current?.focus();
        }
      } else {
        setEmailFeedback({ kind: 'error', message: requestErrorMessage(error, 'Não foi possível solicitar a troca de e-mail. Tente novamente.') });
      }
    } finally {
      setEmailBusy(false);
    }
  }

  const rateLimitMessage = countdown.isWaiting
    ? `Muitas solicitações. ${retryCountdownMessage(countdown.remainingSeconds ?? 0)}`
    : countdown.remainingSeconds === 0
      ? 'Você já pode tentar novamente.'
      : 'Muitas solicitações. Tente novamente mais tarde.';

  return <div className="profile-page">
    <button className="back-link profile-back" type="button" onClick={onBack}><ChevronLeft size={16} aria-hidden="true"/> Meus laboratórios</button>
    <h1 className="profile-title">Seu perfil</h1>

    <section className="profile-card" aria-labelledby="profile-section-title">
      <h2 id="profile-section-title">Dados pessoais</h2>
      <div className="profile-photo-area">
        <div className="profile-photo">{photo ? <img src={photo} alt="Foto de perfil"/> : <UserRound size={30} strokeWidth={1.6} aria-hidden="true"/>}</div>
        <div className="profile-photo-details">
          <strong>Foto de perfil</strong>
          <p>JPG, PNG ou WebP · até 2 MB</p>
        </div>
        <button className="button secondary" type="button" disabled={savingPhoto} onClick={() => fileRef.current?.click()}>{savingPhoto ? 'Enviando…' : 'Trocar foto'}</button>
        <input ref={fileRef} className="sr-only" type="file" accept="image/jpeg,image/png,image/webp" aria-label="Escolher foto de perfil" onChange={(event) => { void selectPhoto(event.target.files?.[0]); event.target.value = ''; }}/>
      </div>
      {photoFeedback && <FeedbackMessage feedback={photoFeedback}/>}
      <form className="profile-form" onSubmit={saveName} noValidate>
            <div className="profile-field">
              <label htmlFor="profile-name">Nome</label>
              <input
                ref={nameRef}
                id="profile-name"
                name="nome"
                autoComplete="name"
                required
                value={name}
                aria-invalid={nameError ? true : undefined}
                aria-describedby={nameError ? 'profile-name-error' : undefined}
                onChange={(event) => { setName(event.target.value); setNameError(null); setNameFeedback(null); }}
              />
              {nameError && <span id="profile-name-error" className="profile-field-error" role="alert">{nameError}</span>}
            </div>
        <FeedbackMessage feedback={nameFeedback}/>
        <div className="profile-form-actions"><button className="button primary" disabled={savingName || !name.trim() || name.trim() === user.nome} title={name.trim() === user.nome ? 'Mude o nome para salvar' : undefined}>
          {savingName ? 'Salvando…' : 'Salvar nome'}
        </button></div>
      </form>
    </section>

    <section className="profile-card" aria-labelledby="change-email-title">
      <div className="profile-card-row">
        <div><h2 id="change-email-title">E-mail</h2><p className="profile-current">{user.email}</p></div>
        <button className="button secondary" type="button" aria-expanded={emailOpen} aria-controls="profile-email-panel" onClick={() => setEmailOpen(open => !open)}>{emailOpen ? 'Cancelar' : <>Trocar <span className="sr-only">e-mail</span></>}</button>
      </div>
      {emailOpen && <div className="profile-panel" id="profile-email-panel">
        <p className="profile-panel-hint">Seu endereço atual continua ativo até você confirmar o novo.</p>
        <form className="profile-form" onSubmit={requestEmailChange} noValidate>
              <div className="profile-field">
                <label htmlFor="new-email">Novo e-mail</label>
                <input ref={newEmailRef} id="new-email" name="novoEmail" type="email" autoComplete="email" required maxLength={255} value={email.next} aria-invalid={emailErrors.newEmail ? true : undefined} aria-describedby={emailErrors.newEmail ? 'new-email-error' : undefined} onChange={(event) => setEmailField('next', event.target.value)}/>
                {emailErrors.newEmail && <span id="new-email-error" className="profile-field-error" role="alert">{emailErrors.newEmail}</span>}
              </div>
              <div className="profile-field">
                <label htmlFor="email-current-password">Senha atual</label>
                <PasswordInput inputRef={emailPasswordRef} id="email-current-password" name="senhaAtual" autoComplete="current-password" required value={email.currentPassword} aria-invalid={emailErrors.currentPassword ? true : undefined} aria-describedby={emailErrors.currentPassword ? 'email-current-password-error' : undefined} onChange={(event) => setEmailField('currentPassword', event.target.value)}/>
                {emailErrors.currentPassword && <span id="email-current-password-error" className="profile-field-error" role="alert">{emailErrors.currentPassword}</span>}
              </div>
              {emailRateLimited
                ? <p className={countdown.isWaiting ? 'profile-feedback error' : 'profile-feedback'} role={countdown.isWaiting ? 'alert' : 'status'}>{rateLimitMessage}</p>
                : <FeedbackMessage feedback={emailFeedback}/>}
          <div className="profile-form-actions"><button className="button primary" disabled={emailBusy || countdown.isWaiting}>
            {emailBusy ? 'Enviando…' : 'Enviar link de confirmação'}
          </button></div>
        </form>
      </div>}
    </section>

    <section className="profile-card" aria-labelledby="change-password-title">
      <div className="profile-card-row">
        <div><h2 id="change-password-title">Senha</h2><p className="profile-current" aria-hidden="true">••••••••</p></div>
        <button className="button secondary" type="button" aria-expanded={passwordOpen} aria-controls="profile-password-panel" onClick={() => setPasswordOpen(open => !open)}>{passwordOpen ? 'Cancelar' : <>Alterar <span className="sr-only">senha</span></>}</button>
      </div>
      {passwordOpen && <div className="profile-panel" id="profile-password-panel">
        <form className="profile-form" onSubmit={changePassword} noValidate>
              <div className="profile-field">
                <label htmlFor="current-password">Senha atual</label>
                <PasswordInput inputRef={currentPasswordRef} id="current-password" name="senhaAtual" autoComplete="current-password" required value={password.current} aria-invalid={passwordErrors.currentPassword ? true : undefined} aria-describedby={passwordErrors.currentPassword ? 'current-password-error' : undefined} onChange={(event) => setPasswordField('current', event.target.value)}/>
                {passwordErrors.currentPassword && <span id="current-password-error" className="profile-field-error" role="alert">{passwordErrors.currentPassword}</span>}
              </div>
              <div className="profile-field">
                <label htmlFor="new-password">Nova senha</label>
                <PasswordInput inputRef={newPasswordRef} id="new-password" name="novaSenha" autoComplete="new-password" required maxLength={64} value={password.next} aria-invalid={passwordErrors.newPassword ? true : undefined} aria-describedby={passwordErrors.newPassword ? 'new-password-error' : 'new-password-hint'} onChange={(event) => setPasswordField('next', event.target.value)}/>
                {passwordErrors.newPassword
                  ? <span id="new-password-error" className="profile-field-error" role="alert">{passwordErrors.newPassword}</span>
                  : <span id="new-password-hint" className="profile-field-hint">Use de 8 a 64 caracteres.</span>}
              </div>
              <div className="profile-field">
                <label htmlFor="confirm-new-password">Confirme sua nova senha</label>
                <PasswordInput inputRef={confirmationRef} id="confirm-new-password" name="confirmacaoSenha" autoComplete="new-password" required maxLength={64} value={password.confirmation} aria-invalid={passwordErrors.confirmation ? true : undefined} aria-describedby={passwordErrors.confirmation ? 'confirm-new-password-error' : undefined} onChange={(event) => setPasswordField('confirmation', event.target.value)}/>
                {passwordErrors.confirmation && <span id="confirm-new-password-error" className="profile-field-error" role="alert">{passwordErrors.confirmation}</span>}
              </div>
          <FeedbackMessage feedback={passwordFeedback}/>
          <div className="profile-form-actions"><button className="button primary" disabled={savingPassword}>
            {savingPassword ? 'Salvando…' : 'Salvar nova senha'}
          </button></div>
        </form>
      </div>}
    </section>
  </div>;
}

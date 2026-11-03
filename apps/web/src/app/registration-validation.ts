export type RegistrationField = 'nome' | 'email' | 'senha' | 'confirmacaoSenha';

export interface RegistrationValues {
  nome: string;
  email: string;
  senha: string;
  confirmacaoSenha: string;
}

export type RegistrationErrors = Partial<Record<RegistrationField, string>>;

export function isValidEmail(value: string): boolean {
  if (!value.trim()) return false;
  const input = document.createElement('input');
  input.type = 'email';
  input.value = value.trim();
  return input.validity.valid;
}

export function passwordPolicyError(password: string): string | undefined {
  const characterCount = Array.from(password).length;
  if (characterCount < 8 || characterCount > 64) {
    return 'A senha deve ter entre 8 e 64 caracteres.';
  }
  if (new TextEncoder().encode(password).length > 72) {
    return 'A senha excede o limite de 72 bytes em UTF-8 do BCrypt.';
  }
  return undefined;
}

export function validateRegistration(values: RegistrationValues): RegistrationErrors {
  const errors: RegistrationErrors = {};

  if (!values.nome.trim()) {
    errors.nome = 'Informe seu nome.';
  } else if (values.nome.length > 100) {
    errors.nome = 'O nome deve ter no máximo 100 caracteres.';
  }

  if (!values.email.trim()) {
    errors.email = 'Informe seu e-mail.';
  } else if (!isValidEmail(values.email)) {
    errors.email = 'Informe um e-mail válido.';
  }

  const passwordError = passwordPolicyError(values.senha);
  if (passwordError) errors.senha = passwordError;

  if (!values.confirmacaoSenha) {
    errors.confirmacaoSenha = 'Confirme sua senha.';
  } else if (values.senha !== values.confirmacaoSenha) {
    errors.confirmacaoSenha = 'As senhas não coincidem.';
  }

  return errors;
}

export function validateResendEmail(email: string): string | undefined {
  if (!email.trim()) return 'Informe seu e-mail.';
  if (email.length > 255) return 'O e-mail deve ter no máximo 255 caracteres.';
  if (!isValidEmail(email)) return 'Informe um e-mail válido.';
  return undefined;
}

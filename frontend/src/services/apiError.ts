import axios from "axios";

/** Contrat d'erreur de l'API (RFC 7807 « problem+json ») tel que le renvoie GlobalApiExceptionHandler. */
interface Problem {
  detail?: string;
  code?: string;
  requestId?: string;
  erreurs?: Record<string, string>;
  /** Ancien champ, conservé pour les réponses qui ne viendraient pas du backend GesStock. */
  message?: string;
}

export interface ApiError {
  /** Statut HTTP, ou null si le serveur n'a pas répondu. */
  status: number | null;
  /** Code stable (ex. STOCK_INSUFFISANT, CSRF_INVALIDE, RESEAU) sur lequel le front peut décider. */
  code: string | null;
  /** Texte destiné à l'utilisateur. */
  message: string;
  /** Erreurs de validation par champ (nom du champ -> message). */
  fieldErrors: Record<string, string>;
  /** Identifiant à communiquer au support pour retrouver la trace côté serveur. */
  requestId: string | null;
}

export function parseApiError(error: unknown, fallback = "Une erreur est survenue."): ApiError {
  if (axios.isAxiosError<Problem>(error)) {
    if (!error.response) {
      return {
        status: null,
        code: "RESEAU",
        message: "Le serveur est injoignable. Vérifiez votre connexion puis réessayez.",
        fieldErrors: {},
        requestId: null,
      };
    }
    const data = error.response.data;
    const body: Problem = typeof data === "object" && data !== null ? data : {};
    return {
      status: error.response.status,
      code: body.code ?? null,
      message: body.detail ?? body.message ?? fallback,
      fieldErrors: body.erreurs ?? {},
      requestId: body.requestId ?? (error.response.headers?.["x-request-id"] as string | undefined) ?? null,
    };
  }
  return { status: null, code: null, message: fallback, fieldErrors: {}, requestId: null };
}

/**
 * Un téléchargement (responseType « blob ») qui échoue renvoie l'erreur du serveur DANS un blob : on le relit pour que le message
 * du serveur (« période trop longue »…) arrive à l'écran au lieu d'un texte de repli. À appeler avant apiErrorMessage.
 */
export async function lireErreurDeFichier(error: unknown): Promise<unknown> {
  if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
    try {
      error.response.data = JSON.parse(await error.response.data.text());
    } catch {
      // corps qui n'est pas du JSON : le texte de repli suffit
    }
  }
  return error;
}

/** Le message à afficher : celui du serveur s'il en a donné un, sinon le texte de repli de l'écran. */
export function apiErrorMessage(error: unknown, fallback = "Une erreur est survenue."): string {
  return parseApiError(error, fallback).message;
}

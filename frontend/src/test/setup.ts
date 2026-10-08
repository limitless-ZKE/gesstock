import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Testing Library ne nettoie seul le DOM que si les fonctions de test sont globales : on le fait explicitement.
afterEach(() => cleanup());

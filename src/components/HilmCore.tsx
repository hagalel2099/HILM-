import { useState } from "react";

interface HilmCoreProps {
  onClick?: () => void;
}

const HILM_IMAGE_PATH =
  "/assets/aistudio/file_00000000d1fc820e8c9ebc3789f55e56_2a4d3181.png";

/**
 * Núcleo HILM (Referência 2) integrado diretamente no espaço central do anel HUD,
 * substituindo a esfera original sem adicionar moldura esférica extra ao redor.
 */
export function HilmCore({ onClick }: HilmCoreProps) {
  const [imageError, setImageError] = useState(false);

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="Núcleo HILM — Abrir gaveta de aplicativos ou detalhes do núcleo"
      className="group relative flex items-center justify-center w-[214px] h-[214px] rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-400 cursor-pointer transition-transform duration-200 active:scale-[0.98]"
    >
      {!imageError ? (
        <img
          src={HILM_IMAGE_PATH}
          alt="Núcleo HILM (Hagalel, Íris, Luna, Maya)"
          referrerPolicy="no-referrer"
          onError={() => setImageError(true)}
          className="hilm-core-animate w-full h-full object-contain select-none pointer-events-none"
          style={{
            mixBlendMode: "screen",
            WebkitMaskImage:
              "radial-gradient(circle at center, rgba(0,0,0,1) 50%, rgba(0,0,0,0.82) 68%, rgba(0,0,0,0) 94%)",
            maskImage:
              "radial-gradient(circle at center, rgba(0,0,0,1) 50%, rgba(0,0,0,0.82) 68%, rgba(0,0,0,0) 94%)",
          }}
        />
      ) : (
        <div className="w-40 h-40 rounded-full bg-gradient-to-br from-orange-600/30 via-slate-950 to-violet-600/30 flex items-center justify-center text-xs font-display tracking-widest text-cyan-200">
          HILM
        </div>
      )}
    </button>
  );
}

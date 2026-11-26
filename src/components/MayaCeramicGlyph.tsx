import { useId } from "react";
import type { InstalledAppItem } from "../types/launcher";

interface MayaCeramicGlyphProps {
  app: InstalledAppItem;
  sizeClass?: string;
}

export function MayaCeramicGlyph({
  app,
  sizeClass = "w-5 h-5",
}: MayaCeramicGlyphProps) {
  const rawId = useId();
  const id = `maya3d-${rawId.replace(/[^a-zA-Z0-9_-]/g, "")}`;

  const ceramicSlab = `url(#${id}-slab)`;
  const recessedWell = `url(#${id}-well)`;
  const frameGold = `url(#${id}-frame-gold)`;
  const goldSatin = `url(#${id}-gold-satin)`;
  const goldBright = `url(#${id}-gold-bright)`;
  const copperStruct = `url(#${id}-copper)`;
  const copperDepth = `url(#${id}-copper-depth)`;
  const graphiteBody = `url(#${id}-graphite)`;
  const blueNode = `url(#${id}-blue)`;
  const castShadow = `url(#${id}-shadow)`;

  const pkg = app.packageName.toLowerCase();

  const renderSculptedObject = () => {
    switch (app.iconKey) {
      case "phone":
        return (
          <g filter={castShadow}>
            {/* 3D extruded copper under-chassis */}
            <path
              transform="translate(0.95, 1.35)"
              fill={copperDepth}
              d="M12.2 10.6h3.2c.65 0 1.2.44 1.35 1.07l.85 3.35c.13.52-.06 1.07-.48 1.41l-1.72 1.42a12.6 12.6 0 0 0 6.84 6.84l1.42-1.72c.34-.42.89-.61 1.41-.48l3.35.85c.63.15 1.07.7 1.07 1.35v3.2c0 1.02-.88 1.82-1.9 1.72-9.6-.82-15.4-6.62-16.22-16.22-.1-1.02.7-1.9 1.72-1.9z"
            />
            {/* Main satin golden-honey ceramic handset */}
            <path
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.45"
              d="M12.2 10.6h3.2c.65 0 1.2.44 1.35 1.07l.85 3.35c.13.52-.06 1.07-.48 1.41l-1.72 1.42a12.6 12.6 0 0 0 6.84 6.84l1.42-1.72c.34-.42.89-.61 1.41-.48l3.35.85c.63.15 1.07.7 1.07 1.35v3.2c0 1.02-.88 1.82-1.9 1.72-9.6-.82-15.4-6.62-16.22-16.22-.1-1.02.7-1.9 1.72-1.9z"
            />
            {/* Milled dark graphite acoustic ear/mouth pads */}
            <rect x="12.2" y="12.1" width="2.8" height="3.2" rx="1" fill={graphiteBody} />
            <rect x="24.8" y="25.0" width="3.2" height="2.8" rx="1" fill={graphiteBody} />
            {/* Metallic copper grip collar */}
            <path
              d="M16.5 20.6l2.9 2.9"
              stroke={copperStruct}
              strokeWidth="1.5"
              strokeLinecap="round"
            />
            {/* Sculpted acoustic arcs + electric-blue signal node */}
            <path
              d="M22.2 12.5a5.5 5.5 0 0 1 5.3 5.3"
              fill="none"
              stroke={goldBright}
              strokeWidth="1.35"
              strokeLinecap="round"
            />
            <path
              d="M22.2 15.3a2.7 2.7 0 0 1 2.5 2.5"
              fill="none"
              stroke={copperStruct}
              strokeWidth="1.15"
              strokeLinecap="round"
            />
            <circle cx="22.1" cy="17.8" r="1.25" fill={blueNode} />
          </g>
        );

      case "message":
        return (
          <g filter={castShadow}>
            {/* Extruded copper lower wall */}
            <path
              transform="translate(0.95, 1.4)"
              fill={copperDepth}
              d="M10.4 10.6h19.2a2.6 2.6 0 0 1 2.6 2.6v11.0a2.6 2.6 0 0 1-2.6 2.6H16.8l-5.6 4.4v-4.4h-.8a2.6 2.6 0 0 1-2.6-2.6V13.2a2.6 2.6 0 0 1 2.6-2.6z"
            />
            {/* Satin golden-honey ceramic bubble block */}
            <path
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.48"
              d="M10.4 10.6h19.2a2.6 2.6 0 0 1 2.6 2.6v11.0a2.6 2.6 0 0 1-2.6 2.6H16.8l-5.6 4.4v-4.4h-.8a2.6 2.6 0 0 1-2.6-2.6V13.2a2.6 2.6 0 0 1 2.6-2.6z"
            />
            {/* Recessed dark graphite ceramic screen plate */}
            <rect
              x="10.4"
              y="13.1"
              width="19.2"
              height="11.2"
              rx="1.8"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="0.7"
            />
            {/* Raised metallic message bars */}
            <rect x="12.8" y="15.8" width="13.6" height="2.0" rx="1" fill={goldBright} />
            <rect x="12.8" y="19.5" width="9.2" height="2.0" rx="1" fill={copperStruct} />
            {/* Tiny electric-blue status LED */}
            <circle cx="25.3" cy="20.5" r="1.25" fill={blueNode} />
          </g>
        );

      case "chat":
        return (
          <g filter={castShadow}>
            {/* 3D extruded copper base ring */}
            <path
              transform="translate(0.95, 1.35)"
              fill={copperDepth}
              d="M20 8.6a11.1 11.1 0 0 0-9.65 16.65L8.7 31.3l6.2-1.62A11.1 11.1 0 1 0 20 8.6z"
            />
            {/* Satin golden-honey ceramic medallion shell */}
            <path
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.48"
              d="M20 8.6a11.1 11.1 0 0 0-9.65 16.65L8.7 31.3l6.2-1.62A11.1 11.1 0 1 0 20 8.6z"
            />
            {/* Concave dark-graphite inner basin */}
            <circle
              cx="20"
              cy="19.7"
              r="8.35"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="0.85"
            />
            {/* Sculpted metallic WhatsApp handset in high relief */}
            <path
              transform="translate(0.55, 0.75)"
              fill={copperDepth}
              d="M16.2 14.9c-.25-.55-.5-.56-.74-.57h-.62c-.22 0-.58.08-.88.41-.3.33-1.15 1.12-1.15 2.74s1.18 3.18 1.34 3.4c.16.22 2.28 3.65 5.63 4.97 2.79 1.1 3.36.88 3.96.82.6-.05 1.96-.8 2.23-1.57.28-.77.28-1.43.2-1.57-.08-.14-.3-.22-.63-.38-.33-.17-1.96-.97-2.26-1.08-.3-.11-.52-.16-.74.17-.22.33-.85 1.07-1.04 1.29-.19.22-.39.25-.71.08-.33-.16-1.39-.51-2.65-1.63-.98-.87-1.64-1.95-1.83-2.28-.19-.33-.02-.51.14-.67.15-.15.33-.38.5-.58.16-.19.22-.33.33-.55.11-.22.05-.41-.03-.58-.08-.16-.72-1.78-1.01-2.44z"
            />
            <path
              fill={goldBright}
              d="M16.2 14.9c-.25-.55-.5-.56-.74-.57h-.62c-.22 0-.58.08-.88.41-.3.33-1.15 1.12-1.15 2.74s1.18 3.18 1.34 3.4c.16.22 2.28 3.65 5.63 4.97 2.79 1.1 3.36.88 3.96.82.6-.05 1.96-.8 2.23-1.57.28-.77.28-1.43.2-1.57-.08-.14-.3-.22-.63-.38-.33-.17-1.96-.97-2.26-1.08-.3-.11-.52-.16-.74.17-.22.33-.85 1.07-1.04 1.29-.19.22-.39.25-.71.08-.33-.16-1.39-.51-2.65-1.63-.98-.87-1.64-1.95-1.83-2.28-.19-.33-.02-.51.14-.67.15-.15.33-.38.5-.58.16-.19.22-.33.33-.55.11-.22.05-.41-.03-.58-.08-.16-.72-1.78-1.01-2.44z"
            />
            <circle cx="27.3" cy="12.4" r="1.2" fill={blueNode} />
          </g>
        );

      case "instagram":
        return (
          <g filter={castShadow}>
            {/* Extruded 3D copper chassis depth */}
            <rect
              x="9.95"
              y="10.4"
              width="22"
              height="22"
              rx="6.2"
              fill={copperDepth}
            />
            {/* Satin golden-honey ceramic camera block */}
            <rect
              x="9"
              y="9"
              width="22"
              height="22"
              rx="6.2"
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
            />
            {/* Dark graphite recessed camera face */}
            <rect
              x="11.3"
              y="11.3"
              width="17.4"
              height="17.4"
              rx="4.4"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="0.75"
            />
            {/* Raised metallic gold lens bezel */}
            <circle
              cx="20"
              cy="20"
              r="5.6"
              fill={copperDepth}
              stroke={goldBright}
              strokeWidth="1.65"
            />
            {/* Deep graphite inner lens + cyan-blue optical glint */}
            <circle cx="20" cy="20" r="3.45" fill="#090D14" stroke={copperStruct} strokeWidth="0.6" />
            <circle cx="18.7" cy="18.7" r="1.15" fill={blueNode} />
            {/* Flash LED stud */}
            <circle cx="25.6" cy="14.4" r="1.35" fill={goldBright} stroke="#141822" strokeWidth="0.45" />
          </g>
        );

      case "youtube":
        return (
          <g filter={castShadow}>
            {/* Extruded copper TV/display chassis */}
            <rect
              x="8.2"
              y="12.1"
              width="25.4"
              height="18.4"
              rx="5.4"
              fill={copperDepth}
            />
            {/* Sculpted satin golden-honey widescreen body */}
            <rect
              x="7.3"
              y="10.8"
              width="25.4"
              height="18.4"
              rx="5.4"
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
            />
            {/* Recessed dark graphite ceramic screen well */}
            <rect
              x="9.4"
              y="12.9"
              width="21.2"
              height="14.2"
              rx="3.7"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="0.7"
            />
            {/* 3D elevated Play wedge (extruded copper base + bright gold-honey face) */}
            <polygon
              transform="translate(0.85, 1.15)"
              points="16.5,14.8 26.2,20.0 16.5,25.2"
              fill={copperDepth}
            />
            <polygon
              points="16.5,14.8 26.2,20.0 16.5,25.2"
              fill={goldBright}
              stroke="#FFF2C6"
              strokeWidth="0.5"
              strokeLinejoin="round"
            />
            {/* Tiny electric-blue receiver LED */}
            <circle cx="28.3" cy="27.6" r="1.05" fill={blueNode} />
          </g>
        );

      case "music":
        if (pkg.includes("spotify")) {
          return (
            <g filter={castShadow}>
              {/* 3D extruded copper disc depth */}
              <circle cx="20.9" cy="21.2" r="11.5" fill={copperDepth} />
              {/* Satin golden-honey ceramic outer rim */}
              <circle
                cx="20"
                cy="20"
                r="11.5"
                fill={goldSatin}
                stroke="#FFEBB3"
                strokeWidth="0.5"
              />
              {/* Concave dark-graphite acoustic disc */}
              <circle
                cx="20"
                cy="20"
                r="9.35"
                fill={graphiteBody}
                stroke={copperStruct}
                strokeWidth="0.8"
              />
              {/* 3 iconic sculpted Spotify acoustic waves with copper depth + gold-honey ridge */}
              <g transform="translate(0.45, 0.7)">
                <path d="M13.6 16.6c4.1-1.2 9.0-.9 12.8 1.0" fill="none" stroke={copperDepth} strokeWidth="2.3" strokeLinecap="round" />
                <path d="M14.6 20.1c3.4-.9 7.5-.6 10.6.9" fill="none" stroke={copperDepth} strokeWidth="2.0" strokeLinecap="round" />
                <path d="M15.7 23.4c2.6-.6 5.7-.4 8.0.7" fill="none" stroke={copperDepth} strokeWidth="1.7" strokeLinecap="round" />
              </g>
              <path d="M13.6 16.6c4.1-1.2 9.0-.9 12.8 1.0" fill="none" stroke={goldBright} strokeWidth="2.05" strokeLinecap="round" />
              <path d="M14.6 20.1c3.4-.9 7.5-.6 10.6.9" fill="none" stroke={goldSatin} strokeWidth="1.8" strokeLinecap="round" />
              <path d="M15.7 23.4c2.6-.6 5.7-.4 8.0.7" fill="none" stroke={copperStruct} strokeWidth="1.55" strokeLinecap="round" />
              <circle cx="26.5" cy="17.6" r="1.05" fill={blueNode} />
            </g>
          );
        }
        return (
          <g filter={castShadow}>
            {/* Extruded copper double note */}
            <g transform="translate(0.9, 1.25)">
              <path d="M16.2 25.5V12.2l11.2-2.2v13.0" fill="none" stroke={copperDepth} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
              <circle cx="13.4" cy="26.2" r="3.4" fill={copperDepth} />
              <circle cx="24.6" cy="23.8" r="3.4" fill={copperDepth} />
            </g>
            {/* Golden-honey ceramic stems & beam */}
            <path d="M16.2 25.5V12.2l11.2-2.2v13.0" fill="none" stroke={goldSatin} strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M16.2 15.6l11.2-2.2" fill="none" stroke={copperStruct} strokeWidth="1.5" />
            <circle cx="13.4" cy="26.2" r="3.35" fill={goldBright} stroke={copperStruct} strokeWidth="0.7" />
            <circle cx="24.6" cy="23.8" r="3.35" fill={goldSatin} stroke={copperStruct} strokeWidth="0.7" />
            <circle cx="13.4" cy="26.2" r="1.15" fill={graphiteBody} />
            <circle cx="24.6" cy="23.8" r="1.15" fill={blueNode} />
          </g>
        );

      case "globe":
        if (pkg.includes("chrome")) {
          const chromeBlade =
            "M 15.67 22.5 L 10.66 13.82 A 11.2 11.2 0 0 1 30.02 15.0 L 20 15.0 A 5.0 5.0 0 0 0 15.67 22.5 Z";
          return (
            <g filter={castShadow}>
              {/* Extruded copper sphere rim */}
              <circle cx="20.9" cy="21.25" r="11.3" fill={copperDepth} />
              {/* Dark graphite precision channel backing */}
              <circle cx="20" cy="20" r="11.3" fill={graphiteBody} />
              {/* 3 symmetrical interlocking ceramic Chrome blades */}
              <path
                d={chromeBlade}
                fill={goldBright}
                stroke="#0B0E15"
                strokeWidth="0.95"
                strokeLinejoin="round"
              />
              <path
                d={chromeBlade}
                transform="rotate(120 20 20)"
                fill={copperStruct}
                stroke="#0B0E15"
                strokeWidth="0.95"
                strokeLinejoin="round"
              />
              <path
                d={chromeBlade}
                transform="rotate(240 20 20)"
                fill={goldSatin}
                stroke="#0B0E15"
                strokeWidth="0.95"
                strokeLinejoin="round"
              />
              {/* Raised metallic collar & dark graphite central optic */}
              <circle cx="20" cy="20" r="4.9" fill={graphiteBody} stroke={goldBright} strokeWidth="1.25" />
              <circle cx="20" cy="20" r="2.9" fill="#1D4ED8" stroke="#38BDF8" strokeWidth="0.65" />
              <circle cx="19.3" cy="19.3" r="1.3" fill={blueNode} />
            </g>
          );
        }
        return (
          <g filter={castShadow}>
            <circle cx="20.9" cy="21.2" r="11.2" fill={copperDepth} />
            <circle cx="20" cy="20" r="11.2" fill={graphiteBody} stroke={goldSatin} strokeWidth="1.65" />
            <ellipse cx="20" cy="20" rx="5.2" ry="11.0" fill="none" stroke={goldBright} strokeWidth="1.35" />
            <line x1="9" y1="20" x2="31" y2="20" stroke={copperStruct} strokeWidth="1.4" />
            <line x1="11.5" y1="14.5" x2="28.5" y2="14.5" stroke={goldSatin} strokeWidth="1.05" />
            <line x1="11.5" y1="25.5" x2="28.5" y2="25.5" stroke={goldSatin} strokeWidth="1.05" />
            <circle cx="20" cy="20" r="2.1" fill={blueNode} />
          </g>
        );

      case "camera":
        return (
          <g filter={castShadow}>
            {/* Copper extruded camera housing */}
            <path
              transform="translate(0.95, 1.35)"
              fill={copperDepth}
              d="M9.4 13.5h4.2l1.9-3.2h9.0l1.9 3.2h4.2a2.4 2.4 0 0 1 2.4 2.4v11.6a2.4 2.4 0 0 1-2.4 2.4H9.4a2.4 2.4 0 0 1-2.4-2.4V15.9a2.4 2.4 0 0 1 2.4-2.4z"
            />
            {/* Satin golden-honey ceramic body */}
            <path
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
              d="M9.4 13.5h4.2l1.9-3.2h9.0l1.9 3.2h4.2a2.4 2.4 0 0 1 2.4 2.4v11.6a2.4 2.4 0 0 1-2.4 2.4H9.4a2.4 2.4 0 0 1-2.4-2.4V15.9a2.4 2.4 0 0 1 2.4-2.4z"
            />
            {/* Copper shutter button & recessed camera faceplate */}
            <rect x="9.6" y="11.4" width="3.2" height="1.7" rx="0.7" fill={copperStruct} />
            <rect x="9.2" y="15.8" width="21.6" height="12.0" rx="2.0" fill={graphiteBody} />
            {/* Protruding 3D metallic lens barrel */}
            <circle cx="20.6" cy="22.1" r="5.9" fill={copperDepth} />
            <circle cx="20" cy="21.4" r="5.8" fill={graphiteBody} stroke={goldBright} strokeWidth="1.75" />
            <circle cx="20" cy="21.4" r="3.2" fill="#080B12" stroke={copperStruct} strokeWidth="0.85" />
            <circle cx="18.7" cy="20.2" r="1.2" fill={blueNode} />
            {/* Tiny optical viewfinder / flash LED */}
            <circle cx="28.0" cy="17.2" r="1.25" fill={blueNode} />
          </g>
        );

      case "image":
        return (
          <g filter={castShadow}>
            {/* Copper extruded gallery slab */}
            <rect x="9.4" y="11.2" width="23.2" height="19.6" rx="3.4" fill={copperDepth} />
            {/* Satin golden-honey metallic picture bezel */}
            <rect
              x="8.4"
              y="10.0"
              width="23.2"
              height="19.6"
              rx="3.4"
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
            />
            {/* Dark graphite recessed canvas */}
            <rect
              x="10.5"
              y="12.1"
              width="19.0"
              height="15.4"
              rx="2.0"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="0.65"
            />
            {/* Sculpted metallic sun */}
            <circle cx="24.9" cy="16.1" r="2.1" fill={goldBright} />
            {/* Rear copper mountain peak */}
            <path d="M18.6 27.3l5.4-7.2 5.4 7.2z" fill={copperStruct} />
            {/* Front sculpted golden-honey mountain ridge */}
            <path
              d="M10.7 27.3l5.6-8.3 5.2 6.0 2.0-2.0 3.2 4.3z"
              fill={goldBright}
              stroke="#FFF2C6"
              strokeWidth="0.35"
              strokeLinejoin="round"
            />
            <circle cx="13.5" cy="15.0" r="1.0" fill={blueNode} />
          </g>
        );

      case "map":
        return (
          <g filter={castShadow}>
            {/* Tilted dark-graphite & copper compass base pad */}
            <ellipse
              cx="20.3"
              cy="28.4"
              rx="9.4"
              ry="3.1"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="1.05"
            />
            {/* Extruded copper pin depth */}
            <path
              transform="translate(0.95, 1.2)"
              fill={copperDepth}
              d="M20 28.2s7.8-6.9 7.8-12.5a7.8 7.8 0 1 0-15.6 0c0 5.6 7.8 12.5 7.8 12.5z"
            />
            {/* Satin golden-honey 3D pin body */}
            <path
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
              d="M20 28.2s7.8-6.9 7.8-12.5a7.8 7.8 0 1 0-15.6 0c0 5.6 7.8 12.5 7.8 12.5z"
            />
            {/* Recessed dark graphite collar */}
            <circle cx="20" cy="15.7" r="3.9" fill={graphiteBody} stroke={copperStruct} strokeWidth="0.85" />
            {/* Luminous electric-blue GPS beacon */}
            <circle cx="20" cy="15.7" r="1.85" fill={blueNode} />
          </g>
        );

      case "calendar":
        return (
          <g filter={castShadow}>
            {/* Copper extruded calendar chassis */}
            <rect x="9.8" y="12.1" width="22.4" height="19.6" rx="3.4" fill={copperDepth} />
            {/* Satin golden-honey lower body */}
            <rect
              x="8.8"
              y="10.9"
              width="22.4"
              height="19.6"
              rx="3.4"
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
            />
            {/* Dark graphite recessed page */}
            <rect x="10.6" y="15.9" width="18.8" height="12.8" rx="1.8" fill={graphiteBody} />
            {/* Metallic copper header rail */}
            <path
              d="M9.0 14.2a3.2 3.2 0 0 1 3.2-3.2h15.6a3.2 3.2 0 0 1 3.2 3.2v1.9H9.0z"
              fill={copperStruct}
            />
            {/* Sculpted binder rings */}
            <rect x="13.8" y="8.2" width="2.4" height="4.6" rx="1.2" fill={goldBright} stroke="#131720" strokeWidth="0.4" />
            <rect x="23.8" y="8.2" width="2.4" height="4.6" rx="1.2" fill={goldBright} stroke="#131720" strokeWidth="0.4" />
            {/* 3D raised day tiles */}
            <rect x="12.6" y="18.0" width="3.6" height="3.2" rx="0.8" fill={goldSatin} />
            <rect x="18.2" y="18.0" width="3.6" height="3.2" rx="0.8" fill={goldBright} />
            <rect x="23.8" y="18.0" width="3.6" height="3.2" rx="0.8" fill={copperStruct} />
            <rect x="12.6" y="23.0" width="3.6" height="3.2" rx="0.8" fill={copperStruct} />
            <rect x="18.2" y="23.0" width="3.6" height="3.2" rx="0.8" fill={blueNode} />
            <rect x="23.8" y="23.0" width="3.6" height="3.2" rx="0.8" fill={goldSatin} />
          </g>
        );

      case "notes":
        return (
          <g filter={castShadow}>
            {/* Copper extruded note pad */}
            <path
              transform="translate(0.9, 1.3)"
              fill={copperDepth}
              d="M10.5 9.2h12.2l5.8 5.8v14.5a2.2 2.2 0 0 1-2.2 2.2H10.5a2.2 2.2 0 0 1-2.2-2.2V11.4a2.2 2.2 0 0 1 2.2-2.2z"
            />
            {/* Satin golden-honey note slate */}
            <path
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.45"
              d="M10.5 9.2h12.2l5.8 5.8v14.5a2.2 2.2 0 0 1-2.2 2.2H10.5a2.2 2.2 0 0 1-2.2-2.2V11.4a2.2 2.2 0 0 1 2.2-2.2z"
            />
            {/* Dark graphite recessed writing well */}
            <path
              fill={graphiteBody}
              d="M11.6 11.4h10.2l4.5 4.5v13.2a1.1 1.1 0 0 1-1.1 1.1H11.6a1.1 1.1 0 0 1-1.1-1.1V12.5a1.1 1.1 0 0 1 1.1-1.1z"
            />
            {/* Folded copper corner flap */}
            <path d="M22.7 9.2v5.8h5.8z" fill={copperStruct} />
            {/* Ruled metallic lines */}
            <rect x="12.8" y="15.2" width="8.6" height="1.7" rx="0.85" fill={goldBright} />
            <rect x="12.8" y="19.1" width="10.4" height="1.7" rx="0.85" fill={copperStruct} />
            <rect x="12.8" y="23.0" width="6.8" height="1.7" rx="0.85" fill={goldSatin} />
            {/* 3D sculpted technological stylus overlaid diagonally */}
            <path
              d="M22.6 25.0l7.4-7.4 2.4 2.4-7.4 7.4-3.3.9z"
              fill={goldBright}
              stroke={copperDepth}
              strokeWidth="0.65"
              strokeLinejoin="round"
            />
            <circle cx="21.9" cy="28.1" r="1.0" fill={blueNode} />
          </g>
        );

      case "folder":
        return (
          <g filter={castShadow}>
            {/* Back copper tab plate */}
            <path
              fill={copperStruct}
              stroke="#FFE49E"
              strokeWidth="0.4"
              d="M9.6 10.6h6.4l2.4 2.8h12.0a2.2 2.2 0 0 1 2.2 2.2v12.4a2.2 2.2 0 0 1-2.2 2.2H9.6a2.2 2.2 0 0 1-2.2-2.2V12.8a2.2 2.2 0 0 1 2.2-2.2z"
            />
            {/* Dark graphite document divider edge */}
            <rect x="9.6" y="14.2" width="20.8" height="3.0" rx="0.9" fill={graphiteBody} />
            {/* Extruded shadow below front ceramic flap */}
            <rect x="8.3" y="16.8" width="24.9" height="13.4" rx="2.6" fill={copperDepth} />
            {/* Front beveled satin golden-honey ceramic flap */}
            <rect
              x="7.4"
              y="15.7"
              width="25.2"
              height="13.4"
              rx="2.6"
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
            />
            {/* Dark graphite machined handle recess & electric-blue lock LED */}
            <rect x="14.2" y="21.1" width="11.6" height="2.6" rx="1.3" fill={graphiteBody} />
            <circle cx="28.5" cy="18.6" r="1.15" fill={blueNode} />
          </g>
        );

      case "code":
        return (
          <g filter={castShadow}>
            {/* GitHub 3D sculpted ceramic Octocat medallion */}
            <circle cx="20.85" cy="21.1" r="11.4" fill={copperDepth} />
            <circle
              cx="20"
              cy="20"
              r="11.4"
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
            />
            {/* Concave dark-graphite inner disc */}
            <circle
              cx="20"
              cy="20"
              r="9.3"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="0.8"
            />
            {/* Sculpted 3D Octocat bust in golden-honey & copper depth */}
            <path
              transform="translate(0.55, 0.8)"
              fill={copperDepth}
              d="M20 12.1c-4.4 0-7.9 3.5-7.9 7.9 0 3.48 2.25 6.43 5.38 7.47.4.07.54-.17.54-.38 0-.19-.01-.82-.01-1.49-1.97.36-2.49-.48-2.64-.92-.09-.23-.48-.92-.82-1.11-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.5 7.5 0 0 1 4.0 0c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.14.46.55.38A7.91 7.91 0 0 0 27.9 20c0-4.4-3.5-7.9-7.9-7.9z"
            />
            <path
              fill={goldBright}
              d="M20 12.1c-4.4 0-7.9 3.5-7.9 7.9 0 3.48 2.25 6.43 5.38 7.47.4.07.54-.17.54-.38 0-.19-.01-.82-.01-1.49-1.97.36-2.49-.48-2.64-.92-.09-.23-.48-.92-.82-1.11-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.5 7.5 0 0 1 4.0 0c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.14.46.55.38A7.91 7.91 0 0 0 27.9 20c0-4.4-3.5-7.9-7.9-7.9z"
            />
            {/* Tiny electric-blue repository status node */}
            <circle cx="20" cy="18.2" r="1.05" fill={blueNode} />
          </g>
        );

      case "terminal":
        return (
          <g filter={castShadow}>
            {/* Copper extruded console chassis */}
            <rect x="8.8" y="11.4" width="24.2" height="19.6" rx="3.8" fill={copperDepth} />
            {/* Satin golden-honey console frame */}
            <rect
              x="7.9"
              y="10.2"
              width="24.2"
              height="19.6"
              rx="3.8"
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
            />
            {/* Deep dark-graphite display well */}
            <rect
              x="10.0"
              y="12.5"
              width="20.0"
              height="15.0"
              rx="2.1"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="0.7"
            />
            {/* Sculpted 3D chevron > */}
            <path
              d="M13.5 16.5l4.4 3.5-4.4 3.5"
              fill="none"
              stroke={copperDepth}
              strokeWidth="2.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              transform="translate(0.5, 0.7)"
            />
            <path
              d="M13.5 16.5l4.4 3.5-4.4 3.5"
              fill="none"
              stroke={goldBright}
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            {/* Illuminated electric-blue command cursor */}
            <rect x="20.2" y="21.9" width="6.2" height="2.3" rx="1.1" fill={blueNode} />
            {/* Top copper micro-studs */}
            <circle cx="12.2" cy="14.2" r="0.85" fill={copperStruct} />
            <circle cx="14.8" cy="14.2" r="0.85" fill={goldBright} />
          </g>
        );

      case "discord":
        return (
          <g filter={castShadow}>
            {/* Extruded copper Clyde depth wall */}
            <path
              transform="translate(0.95, 1.35)"
              fill={copperDepth}
              d="M27.3 12.1a15 15 0 0 0-4.3-1.35c-.18.33-.39.77-.53 1.12a14 14 0 0 0-4.94 0c-.14-.35-.35-.79-.54-1.12A15 15 0 0 0 12.7 12.1c-2.75 4.1-3.5 8.1-3.13 12.05 1.82 1.34 3.58 2.15 5.31 2.68.42-.57.79-1.17 1.11-1.8-.6-.22-1.16-.5-1.7-.81l.4-.31a10.9 10.9 0 0 0 10.62 0l.4.31c-.54.31-1.1.59-1.7.81.32.63.69 1.23 1.11 1.8 1.73-.53 3.49-1.34 5.31-2.68.45-4.58-.75-8.54-3.13-12.05z"
            />
            {/* Satin golden-honey ceramic Discord emblem */}
            <path
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.5"
              d="M27.3 12.1a15 15 0 0 0-4.3-1.35c-.18.33-.39.77-.53 1.12a14 14 0 0 0-4.94 0c-.14-.35-.35-.79-.54-1.12A15 15 0 0 0 12.7 12.1c-2.75 4.1-3.5 8.1-3.13 12.05 1.82 1.34 3.58 2.15 5.31 2.68.42-.57.79-1.17 1.11-1.8-.6-.22-1.16-.5-1.7-.81l.4-.31a10.9 10.9 0 0 0 10.62 0l.4.31c-.54.31-1.1.59-1.7.81.32.63.69 1.23 1.11 1.8 1.73-.53 3.49-1.34 5.31-2.68.45-4.58-.75-8.54-3.13-12.05z"
            />
            {/* Recessed dark-graphite eye sockets + glowing electric-blue lenses */}
            <ellipse cx="16.4" cy="19.6" rx="2.45" ry="2.75" fill={graphiteBody} stroke={copperStruct} strokeWidth="0.65" />
            <ellipse cx="23.6" cy="19.6" rx="2.45" ry="2.75" fill={graphiteBody} stroke={copperStruct} strokeWidth="0.65" />
            <circle cx="16.4" cy="19.6" r="1.45" fill={blueNode} />
            <circle cx="23.6" cy="19.6" r="1.45" fill={blueNode} />
          </g>
        );

      case "bank":
        return (
          <g filter={castShadow}>
            {/* Extruded copper thickness of Nu monogram */}
            <path
              transform="translate(0.95, 1.35)"
              fill={copperDepth}
              d="M9.4 11.4c0-1 .82-1.8 1.82-1.8h2.65c1 0 1.82.8 1.82 1.8v8.4l6.75-9.25A2.4 2.4 0 0 1 24.4 9.6h2.5c1 0 1.82.8 1.82 1.8v16.0c0 1-.82 1.8-1.82 1.8h-2.65c-1 0-1.82-.8-1.82-1.8v-8.4l-6.75 9.25a2.4 2.4 0 0 1-1.96.95h-2.5c-1 0-1.82-.8-1.82-1.8v-16.0z"
            />
            {/* Satin golden-honey Nu ceramic ribbon */}
            <path
              fill={goldSatin}
              stroke="#FFEBB3"
              strokeWidth="0.48"
              d="M9.4 11.4c0-1 .82-1.8 1.82-1.8h2.65c1 0 1.82.8 1.82 1.8v8.4l6.75-9.25A2.4 2.4 0 0 1 24.4 9.6h2.5c1 0 1.82.8 1.82 1.8v16.0c0 1-.82 1.8-1.82 1.8h-2.65c-1 0-1.82-.8-1.82-1.8v-8.4l-6.75 9.25a2.4 2.4 0 0 1-1.96.95h-2.5c-1 0-1.82-.8-1.82-1.8v-16.0z"
            />
            {/* Contrasting dark-graphite structural inlay groove */}
            <path
              d="M12.6 12.6v14.8M25.5 12.6v14.8"
              stroke={graphiteBody}
              strokeWidth="1.25"
              strokeLinecap="round"
            />
            <circle cx="25.5" cy="12.2" r="1.1" fill={blueNode} />
          </g>
        );

      case "settings":
      default:
        return (
          <g filter={castShadow}>
            {/* 3D extruded copper 8-tooth precision gear */}
            <g transform="translate(0.95, 1.35)">
              <circle cx="20" cy="20" r="8.9" fill={copperDepth} />
              {[0, 45, 90, 135].map((deg) => (
                <rect
                  key={deg}
                  x="18.1"
                  y="8.4"
                  width="3.8"
                  height="23.2"
                  rx="1.0"
                  fill={copperDepth}
                  transform={`rotate(${deg} 20 20)`}
                />
              ))}
            </g>
            {/* Satin golden-honey ceramic gear body */}
            <g>
              {[0, 45, 90, 135].map((deg) => (
                <rect
                  key={deg}
                  x="18.1"
                  y="8.4"
                  width="3.8"
                  height="23.2"
                  rx="1.0"
                  fill={goldSatin}
                  stroke="#FFEBB3"
                  strokeWidth="0.35"
                  transform={`rotate(${deg} 20 20)`}
                />
              ))}
              <circle
                cx="20"
                cy="20"
                r="8.85"
                fill={goldSatin}
                stroke="#FFEBB3"
                strokeWidth="0.45"
              />
            </g>
            {/* Stepped dark-graphite inner bearing race */}
            <circle
              cx="20"
              cy="20"
              r="5.6"
              fill={graphiteBody}
              stroke={copperStruct}
              strokeWidth="0.95"
            />
            <circle
              cx="20"
              cy="20"
              r="3.2"
              fill="#090C12"
              stroke={goldBright}
              strokeWidth="0.85"
            />
            {/* Electric-blue rotor core */}
            <circle cx="20" cy="20" r="1.4" fill={blueNode} />
          </g>
        );
    }
  };

  return (
    <span
      className={`${sizeClass} relative rounded-[22%] bg-[#131720] shadow-[0_6px_12px_rgba(0,0,0,0.9),0_1px_0_rgba(255,224,140,0.2)] flex items-center justify-center shrink-0 overflow-hidden select-none`}
    >
      <svg
        viewBox="0 0 40 40"
        className="w-full h-full"
        aria-hidden="true"
      >
        <defs>
          {/* Preserved dark-graphite satin ceramic outer slab */}
          <linearGradient id={`${id}-slab`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#262D3B" />
            <stop offset="40%" stopColor="#161A24" />
            <stop offset="100%" stopColor="#0B0E14" />
          </linearGradient>

          {/* Deep concave graphite inner well */}
          <linearGradient id={`${id}-well`} x1="15%" y1="10%" x2="88%" y2="95%">
            <stop offset="0%" stopColor="#0B0E15" />
            <stop offset="65%" stopColor="#131823" />
            <stop offset="100%" stopColor="#1C2331" />
          </linearGradient>

          {/* Preserved outer golden-honey chamfered frame */}
          <linearGradient id={`${id}-frame-gold`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#FFE8A3" />
            <stop offset="35%" stopColor="#F5B942" />
            <stop offset="72%" stopColor="#C47A48" />
            <stop offset="100%" stopColor="#E6A935" />
          </linearGradient>

          {/* Satin technological ceramic golden-honey body */}
          <linearGradient id={`${id}-gold-satin`} x1="12%" y1="6%" x2="88%" y2="94%">
            <stop offset="0%" stopColor="#FFE8A6" />
            <stop offset="32%" stopColor="#F5B942" />
            <stop offset="70%" stopColor="#CF8A3B" />
            <stop offset="100%" stopColor="#9A5B2C" />
          </linearGradient>

          {/* Brushed metallic gold highlights for structural contrast */}
          <linearGradient id={`${id}-gold-bright`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#FFF5CC" />
            <stop offset="45%" stopColor="#F8C65B" />
            <stop offset="100%" stopColor="#C87E43" />
          </linearGradient>

          {/* Structural copper metal accents */}
          <linearGradient id={`${id}-copper`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#DFA06E" />
            <stop offset="50%" stopColor="#C47A48" />
            <stop offset="100%" stopColor="#854925" />
          </linearGradient>

          {/* Extruded copper-bronze depth wall for 3D Z-thickness */}
          <linearGradient id={`${id}-copper-depth`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#7C4321" />
            <stop offset="60%" stopColor="#522B15" />
            <stop offset="100%" stopColor="#2A160B" />
          </linearGradient>

          {/* Satin dark graphite ceramic recessed inserts */}
          <linearGradient id={`${id}-graphite`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#1E2533" />
            <stop offset="55%" stopColor="#121620" />
            <stop offset="100%" stopColor="#090B10" />
          </linearGradient>

          {/* Controlled pinpoint electric-blue luminous focal dot */}
          <radialGradient id={`${id}-blue`} cx="40%" cy="38%" r="60%">
            <stop offset="0%" stopColor="#E0F7FF" />
            <stop offset="45%" stopColor="#38BDF8" />
            <stop offset="100%" stopColor="#2563EB" />
          </radialGradient>

          {/* 3D recessed contact shadow inside the ceramic well */}
          <filter id={`${id}-shadow`} x="-22%" y="-22%" width="144%" height="144%">
            <feDropShadow
              dx="0.6"
              dy="1.35"
              stdDeviation="0.95"
              floodColor="#020306"
              floodOpacity="0.92"
            />
          </filter>
        </defs>

        {/* Preserved outer matte dark-graphite ceramic slab */}
        <rect x="0.8" y="0.8" width="38.4" height="38.4" rx="9" fill={ceramicSlab} />

        {/* Preserved golden-honey outer inlay trim */}
        <rect
          x="1.6"
          y="1.6"
          width="36.8"
          height="36.8"
          rx="8.2"
          fill="none"
          stroke={frameGold}
          strokeWidth="1.5"
        />

        {/* Preserved recessed octagonal dark-graphite ceramic inner well */}
        <path
          d="M 8.6 4.4 H 31.4 L 35.6 8.6 V 31.4 L 31.4 35.6 H 8.6 L 4.4 31.4 V 8.6 Z"
          fill={recessedWell}
          stroke="#2E3648"
          strokeWidth="0.7"
        />

        {/* Subtle electric-blue corner circuit traces */}
        <path
          d="M 5.3 12.6 V 9.2 L 9.2 5.3 H 13.4"
          fill="none"
          stroke="#2563EB"
          strokeWidth="1.05"
          strokeLinecap="round"
        />
        <path
          d="M 5.6 11.8 V 9.4 L 9.4 5.6 H 12.6"
          fill="none"
          stroke="#38BDF8"
          strokeWidth="0.58"
          strokeLinecap="round"
        />
        <path
          d="M 34.7 27.4 V 30.8 L 30.8 34.7 H 26.6"
          fill="none"
          stroke="#2563EB"
          strokeWidth="1.05"
          strokeLinecap="round"
        />

        {/* Machined copper corner rivets */}
        <circle cx="32.4" cy="7.6" r="1.0" fill={copperStruct} />
        <circle cx="7.6" cy="32.4" r="1.0" fill={copperStruct} />

        {/* Subtle top satin ceramic bevel edge */}
        <line
          x1="12"
          y1="2.4"
          x2="28"
          y2="2.4"
          stroke="#FFE8A3"
          strokeWidth="0.65"
          strokeOpacity="0.55"
        />

        {/* Sculpted 3D miniature technological ceramic & metal object */}
        {renderSculptedObject()}
      </svg>
    </span>
  );
}

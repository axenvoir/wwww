package com.example.game.render

import android.opengl.GLES30
import android.util.Log

/**
 * OpenGL ES 3.0 Shader Suite for Matchday LAN:
 *  1. SKINNED_PLAYER — 19-bone GPU linear-blend skeletal skinning + textured club kit, face, skin, hair, boots & gloves
 *  2. BALL           — Mathematical truncated-icosahedron 32-panel FIFA match football with stitched seams & leather specular
 *  3. PITCH          — Multi-scale grass blades, mowed stripes, goalmouth wear, crisp FIFA lines & floodlight pools
 *  4. CROWD          — Single-draw-call GPU-animated stadium spectators
 *  5. TEXTURED       — Scrolling pitchside LED advertising boards & rippling woven goal nets
 *  6. ENTITY         — Stadium bowl architecture, cylindrical metallic goal frames, shadows & ground cursors
 */
object StadiumShaders {

    private const val TAG = "StadiumShaders"

    // =========================================================================
    // 1. GPU-SKINNED 3D FOOTBALL PLAYER SHADER (19 BONES)
    // =========================================================================
    const val SKINNED_PLAYER_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        layout(location = 3) in vec4 aJointIndices;
        layout(location = 4) in vec4 aJointWeights;
        layout(location = 5) in float aRegionId;

        uniform mat4 uVpMatrix;
        uniform mat4 uModelMatrix;
        uniform mat4 uBoneMatrices[19];

        out vec3 vWorldPos;
        out vec3 vWorldNormal;
        out vec2 vTexCoord;
        out float vRegionId;

        void main() {
            int j0 = clamp(int(aJointIndices.x + 0.5), 0, 18);
            int j1 = clamp(int(aJointIndices.y + 0.5), 0, 18);
            int j2 = clamp(int(aJointIndices.z + 0.5), 0, 18);
            int j3 = clamp(int(aJointIndices.w + 0.5), 0, 18);

            mat4 skinMat =
                aJointWeights.x * uBoneMatrices[j0] +
                aJointWeights.y * uBoneMatrices[j1] +
                aJointWeights.z * uBoneMatrices[j2] +
                aJointWeights.w * uBoneMatrices[j3];

            vec4 skinnedPos = skinMat * vec4(aPosition, 1.0);
            vec3 skinnedNorm = normalize(mat3(skinMat) * aNormal);

            vec4 worldPos = uModelMatrix * skinnedPos;
            vWorldPos = worldPos.xyz;
            vWorldNormal = normalize(mat3(uModelMatrix) * skinnedNorm);
            vTexCoord = aTexCoord;
            vRegionId = aRegionId;
            gl_Position = uVpMatrix * worldPos;
        }
    """

    const val SKINNED_PLAYER_FRAGMENT = """#version 300 es
        precision highp float;

        in vec3 vWorldPos;
        in vec3 vWorldNormal;
        in vec2 vTexCoord;
        in float vRegionId;

        uniform sampler2D uKitAtlas;
        uniform vec3 uSkinColor;
        uniform vec3 uHairColor;
        uniform vec3 uBootColor;
        uniform vec3 uGloveColor;
        uniform float uIsGoalkeeper;

        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform float uAmbient;
        uniform float uFloodlight;
        uniform vec3 uCameraPos;
        uniform vec3 uSkyColor;

        out vec4 fragColor;

        vec3 acesToneMap(vec3 x) {
            return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
        }

        void main() {
            vec4 tex = texture(uKitAtlas, vTexCoord);
            vec3 albedo = tex.rgb;
            float specStrength = 0.16;
            float specPower = 22.0;
            float wrapDiff = 0.0;

            if (vRegionId < 0.5) {
                // Football kit cloth (jersey, shorts, socks)
                albedo = tex.rgb;
                specStrength = 0.12;
                specPower = 14.0;
            } else if (vRegionId < 1.8) {
                // Skin & sculpted face detail: preserve white eye sclera & dark pupils/eyebrows
                float isEyeWhite = step(0.97, tex.r) * step(0.97, tex.g) * step(0.98, tex.b);
                vec3 tintedSkin = tex.rgb * uSkinColor * 1.10;
                albedo = mix(tintedSkin, vec3(0.97, 0.97, 0.99), isEyeWhite);
                specStrength = 0.22;
                specPower = 28.0;
                wrapDiff = 0.24; // Soft organic skin wrap lighting
            } else if (vRegionId < 2.5) {
                // Hair strands
                albedo = tex.rgb * uHairColor * 1.15;
                specStrength = 0.30;
                specPower = 38.0;
            } else if (vRegionId < 3.5) {
                // Sculpted football cleats: preserve dark carbon soleplate & bright white swoop
                float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
                float isSwoop = smoothstep(0.95, 0.99, luma);
                float isSole = 1.0 - smoothstep(0.18, 0.30, luma);
                vec3 upperRgb = tex.rgb * uBootColor * 1.12;
                albedo = mix(upperRgb, vec3(0.96, 0.97, 0.98), isSwoop);
                albedo = mix(albedo, vec3(0.10, 0.11, 0.13), isSole);
                specStrength = 0.45;
                specPower = 48.0;
            } else {
                // Hands (skin for outfield, bright padded gloves for GK)
                if (uIsGoalkeeper > 0.5) {
                    albedo = mix(uGloveColor, tex.rgb * uGloveColor, 0.35);
                    specStrength = 0.28;
                    specPower = 24.0;
                } else {
                    albedo = uSkinColor * 0.96;
                    specStrength = 0.20;
                    specPower = 24.0;
                    wrapDiff = 0.22;
                }
            }

            vec3 n = normalize(vWorldNormal);
            vec3 l = normalize(uSunDir);
            vec3 v = normalize(uCameraPos - vWorldPos);
            vec3 h = normalize(l + v);

            float ndl = dot(n, l);
            float diff = max((ndl + wrapDiff) / (1.0 + wrapDiff), 0.0);
            float spec = pow(max(dot(n, h), 0.0), specPower) * specStrength;

            // Secondary stadium floodlight fill from opposite tower
            vec3 l2 = normalize(vec3(-l.x, 0.78, -l.z));
            float diff2 = max(dot(n, l2), 0.0) * 0.34 * (0.45 + 0.55 * uFloodlight);
            vec3 h2 = normalize(l2 + v);
            float spec2 = pow(max(dot(n, h2), 0.0), specPower) * specStrength * 0.65 * uFloodlight;

            // Hemisphere fill: lush turf bounce below + stadium sky above
            float hemi = n.y * 0.5 + 0.5;
            vec3 ambientRgb = mix(vec3(0.10, 0.22, 0.12), uSkyColor * 0.85 + vec3(0.36, 0.40, 0.46), hemi) * (uAmbient + 0.12);

            // Crisp broadcast floodlight rim light separating players cleanly from the pitch
            float rim = pow(1.0 - max(dot(n, v), 0.0), 2.6) * (0.22 + 0.28 * uFloodlight);

            vec3 lit = albedo * (ambientRgb + uSunColor * (diff * 0.88 + diff2))
                + uSunColor * (spec + spec2)
                + vec3(0.88, 0.95, 1.0) * rim;

            vec3 toneMapped = acesToneMap(lit * 1.12);
            fragColor = vec4(toneMapped, 1.0);
        }
    """

    // =========================================================================
    // 2. 32-PANEL GEODESIC FIFA MATCH FOOTBALL SHADER
    // =========================================================================
    const val BALL_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec3 aColor;

        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;

        out vec3 vLocalPos;
        out vec3 vWorldPos;
        out vec3 vWorldNormal;

        void main() {
            vLocalPos = normalize(aPosition);
            vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
            vWorldPos = worldPos.xyz;
            vWorldNormal = normalize(mat3(uModelMatrix) * aNormal);
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val BALL_FRAGMENT = """#version 300 es
        precision highp float;

        in vec3 vLocalPos;
        in vec3 vWorldPos;
        in vec3 vWorldNormal;

        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform float uAmbient;
        uniform float uFloodlight;
        uniform vec3 uCameraPos;
        uniform vec3 uSkyColor;

        out vec4 fragColor;

        void main() {
            vec3 p = normalize(vLocalPos);
            // 12 exact unit icosahedron vertices (centers of the 12 classic football pentagons)
            const float a = 0.52573111;
            const float b = 0.85065081;

            float d0 = abs(dot(p, vec3(0.0,  a,  b)));
            float d1 = abs(dot(p, vec3(0.0, -a,  b)));
            float d2 = abs(dot(p, vec3( a,  b, 0.0)));
            float d3 = abs(dot(p, vec3(-a,  b, 0.0)));
            float d4 = abs(dot(p, vec3( b, 0.0,  a)));
            float d5 = abs(dot(p, vec3( b, 0.0, -a)));

            float maxD = max(max(max(d0, d1), max(d2, d3)), max(d4, d5));

            // Second-closest icosahedron axis for crisp hexagon-to-hexagon stitched seams
            float s0 = min(d0, d1); float m0 = max(d0, d1);
            float s1 = min(d2, d3); float m1 = max(d2, d3);
            float s2 = min(d4, d5); float m2 = max(d4, d5);
            float top1 = max(max(m0, m1), m2);
            float top2 = max(min(m0, m1), min(max(m0, m1), m2));
            top2 = max(top2, max(max(s0, s1), s2));

            // Pentagon mask (dark charcoal-navy championship panels with subtle gold/cyan trim)
            float pentagon = smoothstep(0.872, 0.884, maxD);
            float pentBorder = smoothstep(0.856, 0.868, maxD) * (1.0 - pentagon);
            float hexSeam = 1.0 - smoothstep(0.008, 0.024, abs(top1 - top2));
            float pentSeam = 1.0 - smoothstep(0.004, 0.015, abs(maxD - 0.874));
            float seam = max(hexSeam * (1.0 - pentagon), pentSeam);

            vec3 whiteLeather = vec3(0.97, 0.98, 0.99);
            vec3 darkPanel = vec3(0.09, 0.12, 0.18);
            vec3 trimAccent = vec3(0.10, 0.72, 0.56);

            vec3 albedo = mix(whiteLeather, trimAccent, pentBorder * 0.85);
            albedo = mix(albedo, darkPanel, pentagon);
            albedo *= (1.0 - seam * 0.42);

            // Micro-pebbled match ball grain
            float grain = fract(sin(dot(floor(p * 64.0), vec3(12.9898, 78.233, 37.719))) * 43758.5453);
            albedo *= (0.97 + grain * 0.05);

            vec3 n = normalize(vWorldNormal);
            vec3 l = normalize(uSunDir);
            vec3 v = normalize(uCameraPos - vWorldPos);
            vec3 h = normalize(l + v);

            float diff = max(dot(n, l), 0.0);
            float spec = pow(max(dot(n, h), 0.0), 56.0) * (0.55 - seam * 0.35);

            float hemi = n.y * 0.5 + 0.5;
            vec3 ambientRgb = mix(vec3(0.14, 0.28, 0.16), vec3(0.55, 0.60, 0.68), hemi) * (uAmbient + 0.18);
            float rim = pow(1.0 - max(dot(n, v), 0.0), 2.2) * 0.35 * (0.5 + 0.5 * uFloodlight);

            vec3 lit = albedo * (ambientRgb + uSunColor * diff * 0.90) + uSunColor * spec + vec3(0.95, 0.98, 1.0) * rim;
            fragColor = vec4(clamp(lit, 0.0, 1.0), 1.0);
        }
    """

    // =========================================================================
    // 3. REALISTIC FIFA TURF PITCH SHADER (BLADE DETAIL, WEAR, MARKINGS, FLOODLIGHTS)
    // =========================================================================
    const val PITCH_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec3 aColor;

        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;

        out vec3 vWorldPos;

        void main() {
            vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
            vWorldPos = worldPos.xyz;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val PITCH_FRAGMENT = """#version 300 es
        precision highp float;

        in vec3 vWorldPos;

        uniform vec3 uGrassTint;
        uniform vec3 uSunColor;
        uniform float uAmbient;
        uniform float uFloodlight;
        uniform vec3 uSkyColor;
        uniform vec3 uCameraPos;
        uniform float uHighDetail;

        out vec4 fragColor;

        float hash21(vec2 p) {
            p = fract(p * vec2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

        float valueNoise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = hash21(i);
            float b = hash21(i + vec2(1.0, 0.0));
            float c = hash21(i + vec2(0.0, 1.0));
            float d = hash21(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

        float lineMask(float dist, float halfWidth) {
            float fw = max(fwidth(dist), 0.022);
            return 1.0 - smoothstep(halfWidth - fw, halfWidth + fw, abs(dist));
        }

        void main() {
            float x = vWorldPos.x;
            float z = vWorldPos.z;
            float ax = abs(x);
            float az = abs(z);

            float halfL = 52.5;
            float halfW = 34.0;
            bool insidePitch = (ax <= halfL + 0.08) && (az <= halfW + 0.08);

            // Alternating stadium mowed grass bands (5.25m pitch cuts + subtle cross-cut)
            float stripeX = floor((x + halfL) / 5.25);
            float stripeBand = mod(stripeX, 2.0);
            float crossCheck = mod(floor((z + halfW) / 6.8), 2.0);
            float mowTone = mix(0.88, 1.12, stripeBand) * mix(0.975, 1.025, crossCheck);

            // Multi-scale natural grass variation & directional turf blades
            float macroPatch = (valueNoise(vWorldPos.xz * 0.14) - 0.5) * 0.12;
            float midBlades = (valueNoise(vWorldPos.xz * vec2(2.8, 1.4)) - 0.5) * 0.075;
            float microGrain = (uHighDetail > 0.5) ? ((hash21(floor(vWorldPos.xz * 14.0)) - 0.5) * 0.06) : 0.0;

            vec3 turf = uGrassTint * (mowTone + macroPatch) + vec3(midBlades * 0.32, midBlades * 0.85, midBlades * 0.25)
                + vec3(microGrain * 0.35, microGrain * 0.85, microGrain * 0.28);

            // Subtle natural wear in the 6-yard goalmouths and around penalty spots
            float goalMouthDist = length(vec2((ax - (halfL - 1.8)) * 0.9, z * 0.55));
            float wear = exp(-goalMouthDist * goalMouthDist / 7.5) * 0.24;
            turf = mix(turf, vec3(0.24, 0.29, 0.14), wear);

            // Pitch surround apron (darker rich stadium turf outside touchlines)
            if (!insidePitch) {
                float outerTrack = step(halfL + 4.8, ax) + step(halfW + 4.4, az);
                turf = (outerTrack > 0.5)
                    ? vec3(0.11, 0.15, 0.20) + microGrain * 0.4
                    : uGrassTint * 0.64 + vec3(0.01, 0.025, 0.01);
            }

            // Crisp anti-aliased white FIFA pitch markings
            float lw = 0.082;
            float mark = 0.0;

            if (ax <= halfL + 0.1) mark = max(mark, lineMask(az - halfW, lw));
            if (az <= halfW + 0.1) mark = max(mark, lineMask(ax - halfL, lw));
            if (az <= halfW) mark = max(mark, lineMask(x, lw));

            float centerR = length(vec2(x, z));
            mark = max(mark, lineMask(centerR - 9.15, lw));
            mark = max(mark, 1.0 - smoothstep(0.22, 0.30, centerR));

            float penX = halfL - 16.5;
            float penZ = 20.16;
            if (az <= penZ + 0.1 && ax >= penX - 0.1 && ax <= halfL + 0.1) mark = max(mark, lineMask(ax - penX, lw));
            if (ax >= penX && ax <= halfL) mark = max(mark, lineMask(az - penZ, lw));

            float gaX = halfL - 5.5;
            float gaZ = 9.16;
            if (az <= gaZ + 0.1 && ax >= gaX - 0.1 && ax <= halfL + 0.1) mark = max(mark, lineMask(ax - gaX, lw));
            if (ax >= gaX && ax <= halfL) mark = max(mark, lineMask(az - gaZ, lw));

            float penSpotDist = length(vec2(ax - (halfL - 11.0), z));
            mark = max(mark, 1.0 - smoothstep(0.19, 0.27, penSpotDist));
            if (ax < penX) mark = max(mark, lineMask(penSpotDist - 9.15, lw));

            if (ax <= halfL && az <= halfW) {
                float cornerD = length(vec2(halfL - ax, halfW - az));
                mark = max(mark, lineMask(cornerD - 1.08, lw));
            }

            // Painted grass line lets tiny blades peek through
            vec3 baseColor = mix(turf, vec3(0.95, 0.97, 0.94), clamp(mark * 0.92, 0.0, 0.92));

            // 4-corner stadium floodlight illumination pools + broadcast turf grazing sheen
            float d1 = length(vec2(x - 58.0, z - 40.0));
            float d2 = length(vec2(x + 58.0, z - 40.0));
            float d3 = length(vec2(x - 58.0, z + 40.0));
            float d4 = length(vec2(x + 58.0, z + 40.0));
            float floodPool = (
                exp(-d1 * d1 / 4800.0) +
                exp(-d2 * d2 / 4800.0) +
                exp(-d3 * d3 / 4800.0) +
                exp(-d4 * d4 / 4800.0)
            ) * 0.44 * uFloodlight;
            float centerGlow = exp(-(x * x + z * z) / 3000.0) * 0.22 * uFloodlight;

            // Subtle specular sheen along mowed stripe direction under stadium lights
            vec3 viewDir = normalize(uCameraPos - vWorldPos);
            float grazing = pow(1.0 - max(viewDir.y, 0.0), 3.0) * mix(0.06, 0.14, stripeBand) * (0.4 + 0.6 * uFloodlight);

            vec3 lit = baseColor * (uAmbient + 0.58 * uSunColor + vec3(0.93, 0.97, 1.0) * (floodPool + centerGlow))
                + vec3(0.55, 0.72, 0.50) * grazing;

            float camDist = length(uCameraPos - vWorldPos);
            float fogFactor = clamp((camDist - 85.0) / 145.0, 0.0, 0.36);
            vec3 finalRgb = mix(lit, uSkyColor, fogFactor);

            fragColor = vec4(clamp(finalRgb, 0.0, 1.0), 1.0);
        }
    """

    // =========================================================================
    // 4. GPU-ANIMATED BATCHED 3D STADIUM CROWD SHADER
    // =========================================================================
    const val CROWD_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec3 aColor;
        layout(location = 3) in float aPhase;

        uniform mat4 uVpMatrix;
        uniform float uTime;
        uniform float uExcitement;

        out vec3 vWorldPos;
        out vec3 vWorldNormal;
        out vec3 vColor;

        void main() {
            vec3 pos = aPosition;
            float waveFreq = mix(2.4, 9.5, uExcitement);
            float bounceAmp = mix(0.04, 0.36, uExcitement);
            float wave = max(0.0, sin(uTime * waveFreq + aPhase + pos.x * 0.14));
            pos.y += wave * bounceAmp;

            vWorldPos = pos;
            vWorldNormal = aNormal;
            vColor = aColor;
            gl_Position = uVpMatrix * vec4(pos, 1.0);
        }
    """

    const val CROWD_FRAGMENT = """#version 300 es
        precision highp float;

        in vec3 vWorldPos;
        in vec3 vWorldNormal;
        in vec3 vColor;

        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform float uAmbient;
        uniform vec3 uCameraPos;
        uniform vec3 uSkyColor;

        out vec4 fragColor;

        void main() {
            vec3 n = normalize(vWorldNormal);
            float diff = max(dot(n, normalize(uSunDir)), 0.0);
            vec3 lit = vColor * (uAmbient * 0.95 + uSunColor * diff * 0.72 + vec3(0.12, 0.14, 0.18));

            float camDist = length(uCameraPos - vWorldPos);
            float fogFactor = clamp((camDist - 70.0) / 120.0, 0.0, 0.45);
            fragColor = vec4(mix(lit, uSkyColor, fogFactor), 1.0);
        }
    """

    // =========================================================================
    // 5. TEXTURED LED ADVERTISING BOARDS & WOVEN GOAL NET SHADER
    // =========================================================================
    const val TEXTURED_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;

        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;
        uniform float uNetWave;
        uniform float uTime;

        out vec2 vTexCoord;
        out vec3 vWorldPos;

        void main() {
            vec3 pos = aPosition;
            if (uNetWave > 0.001) {
                pos.z += sin(uTime * 11.0 + aTexCoord.x * 6.0 + aTexCoord.y * 5.0) * uNetWave;
            }
            vec4 wp = uModelMatrix * vec4(pos, 1.0);
            vWorldPos = wp.xyz;
            vTexCoord = aTexCoord;
            gl_Position = uMvpMatrix * vec4(pos, 1.0);
        }
    """

    const val TEXTURED_FRAGMENT = """#version 300 es
        precision highp float;

        in vec2 vTexCoord;
        in vec3 vWorldPos;

        uniform sampler2D uTexture;
        uniform vec2 uUvOffset;
        uniform vec4 uTint;
        uniform float uEmissive;

        out vec4 fragColor;

        void main() {
            vec4 tex = texture(uTexture, vTexCoord + uUvOffset);
            if (tex.a < 0.08) discard;
            vec3 rgb = tex.rgb * uTint.rgb * (uEmissive > 0.5 ? 1.15 : 0.92);
            fragColor = vec4(clamp(rgb, 0.0, 1.0), tex.a * uTint.a);
        }
    """

    // =========================================================================
    // 6. LIT 3D ENTITY SHADER (STADIUM ARCHITECTURE, GOALPOSTS, SHADOWS, CURSORS)
    // =========================================================================
    const val ENTITY_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec3 aColor;

        uniform mat4 uMvpMatrix;
        uniform mat4 uModelMatrix;

        out vec3 vWorldPos;
        out vec3 vWorldNormal;
        out vec3 vVertexColor;

        void main() {
            vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
            vWorldPos = worldPos.xyz;
            vWorldNormal = normalize(mat3(uModelMatrix) * aNormal);
            vVertexColor = aColor;
            gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
        }
    """

    const val ENTITY_FRAGMENT = """#version 300 es
        precision highp float;

        in vec3 vWorldPos;
        in vec3 vWorldNormal;
        in vec3 vVertexColor;

        uniform vec4 uBaseColor;
        uniform float uUseVertexColor;
        uniform float uEmissive;
        uniform vec3 uSunDir;
        uniform vec3 uSunColor;
        uniform float uAmbient;
        uniform float uFloodlight;
        uniform vec3 uCameraPos;
        uniform vec3 uSkyColor;

        out vec4 fragColor;

        void main() {
            vec3 baseRgb = mix(uBaseColor.rgb, uBaseColor.rgb * vVertexColor, uUseVertexColor);
            if (uEmissive > 0.5) {
                fragColor = vec4(baseRgb, uBaseColor.a);
                return;
            }

            vec3 n = normalize(vWorldNormal);
            vec3 l = normalize(uSunDir);
            vec3 v = normalize(uCameraPos - vWorldPos);
            vec3 h = normalize(l + v);

            float diff = max(dot(n, l), 0.0);
            float spec = pow(max(dot(n, h), 0.0), 30.0) * 0.28;

            float hemi = n.y * 0.5 + 0.5;
            vec3 ambientRgb = mix(vec3(0.08, 0.16, 0.10), uSkyColor * 0.9 + vec3(0.32, 0.36, 0.42), hemi) * uAmbient;

            float rim = pow(1.0 - max(dot(n, v), 0.0), 2.8) * 0.24 * uFloodlight;
            vec3 lit = baseRgb * (ambientRgb + uSunColor * diff * 0.82) + uSunColor * spec + vec3(0.85, 0.94, 1.0) * rim;

            float camDist = length(uCameraPos - vWorldPos);
            float fogFactor = clamp((camDist - 85.0) / 130.0, 0.0, 0.42);
            vec3 finalRgb = mix(lit, uSkyColor, fogFactor);

            fragColor = vec4(finalRgb, uBaseColor.a);
        }
    """

    fun linkProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)

        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader link error: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            return 0
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile error: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}

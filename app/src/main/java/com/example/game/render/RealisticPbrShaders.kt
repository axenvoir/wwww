package com.example.game.render

object RealisticPbrShaders {
val PITCH_PBR_FRAGMENT: String = """#version 300 es
precision highp float;

in vec3 vWorldPos;

uniform vec3 uGrassTint;
uniform vec3 uSunColor;
uniform float uAmbient;
uniform float uFloodlight;
uniform vec3 uSkyColor;
uniform vec3 uCameraPos;
uniform float uHighDetail;
uniform mat4 uLightSpaceMatrix;
uniform sampler2D uShadowMap;
uniform float uShadowsEnabled;
uniform vec2 uShadowTexelSize;

out vec4 fragColor;

const float PI = 3.14159265359;

float hash12(vec2 p) {
vec3 p3 = fract(vec3(p.xyx) * 0.1031);
p3 += dot(p3, p3.yzx + 33.33);
return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
vec2 i = floor(p);
vec2 f = fract(p);
vec2 u = f * f * (3.0 - 2.0 * f);
return mix(
mix(hash12(i + vec2(0.0, 0.0)), hash12(i + vec2(1.0, 0.0)), u.x),
mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x),
u.y
);
}

float distributionGGX(vec3 N, vec3 H, float roughness) {
float a = roughness * roughness;
float a2 = a * a;
float NdotH = max(dot(N, H), 0.0);
float NdotH2 = NdotH * NdotH;
float denom = (NdotH2 * (a2 - 1.0) + 1.0);
return a2 / max(PI * denom * denom, 0.0001);
}

float geometrySchlickGGX(float NdotV, float roughness) {
float r = roughness + 1.0;
float k = (r * r) / 8.0;
return NdotV / max(NdotV * (1.0 - k) + k, 0.0001);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
return geometrySchlickGGX(max(dot(N, V), 0.0), roughness) *
geometrySchlickGGX(max(dot(N, L), 0.0), roughness);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 acesToneMap(vec3 x) {
float a = 2.51;
float b = 0.03;
float c = 2.43;
float d = 0.59;
float e = 0.14;
return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

float samplePcfShadow(vec3 worldPos, vec3 N, vec3 L) {
    if (uShadowsEnabled < 0.5) return 1.0;
    vec4 lightClip = uLightSpaceMatrix * vec4(worldPos, 1.0);
    vec3 proj = lightClip.xyz / max(lightClip.w, 0.0001);
    proj = proj * 0.5 + 0.5;
    if (proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0 || proj.z > 1.0) {
        return 1.0;
    }
    float bias = max(0.0022 * (1.0 - max(dot(N, L), 0.0)), 0.0007);
    float currentDepth = proj.z - bias;
    vec2 texel = max(uShadowTexelSize, vec2(1.0 / 1024.0));
    float shadow = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float pcfDepth = texture(uShadowMap, proj.xy + vec2(float(x), float(y)) * texel).r;
            shadow += (currentDepth > pcfDepth) ? 0.0 : 1.0;
        }
    }
    return shadow / 9.0;
}
void main() {
vec2 p = vWorldPos.xz;
float halfLen = 52.5;
float halfWid = 34.0;
bool onField = abs(p.x) <= halfLen && abs(p.y) <= halfWid;

float stripeIdx = floor((p.x + halfLen) / 5.25);
float stripeDir = mod(stripeIdx, 2.0) * 2.0 - 1.0;
float stripe = mod(stripeIdx, 2.0);
vec3 grassA = uGrassTint * vec3(0.92, 0.98, 0.90);
vec3 grassB = uGrassTint * vec3(1.08, 1.05, 0.96);
vec3 turfColor = onField ? mix(grassA, grassB, stripe) : uGrassTint * vec3(0.68, 0.72, 0.66);

float bladeNoise = 0.0;
float bladeGradX = 0.0;
float bladeGradZ = 0.0;
if (uHighDetail > 0.5) {
bladeNoise = valueNoise(p * 18.0) * 0.6 + valueNoise(p * 48.0) * 0.4;
bladeGradX = valueNoise(p * 24.0 + vec2(0.05, 0.0)) - valueNoise(p * 24.0 - vec2(0.05, 0.0));
bladeGradZ = valueNoise(p * 24.0 + vec2(0.0, 0.05)) - valueNoise(p * 24.0 - vec2(0.0, 0.05));
turfColor *= mix(0.88, 1.12, bladeNoise);
}

float wearLeft = exp(-length(p - vec2(-49.5, 0.0)) * 0.35);
float wearRight = exp(-length(p - vec2(49.5, 0.0)) * 0.35);
float wearMask = clamp((wearLeft + wearRight) * 0.65, 0.0, 1.0) * (onField ? 1.0 : 0.0);
vec3 soilColor = vec3(0.32, 0.25, 0.16);
turfColor = mix(turfColor, soilColor, wearMask * 0.55);

float aa = max(length(fwidth(p)) * 0.75, 0.015); float lw = 0.12;
float lineMask = 0.0;
if (abs(p.x) <= halfLen + lw && abs(p.y) <= halfWid + lw) {
if (abs(abs(p.x) - halfLen) < lw || abs(abs(p.y) - halfWid) < lw) lineMask = 1.0;
if (abs(p.x) < lw && abs(p.y) <= halfWid) lineMask = 1.0;
float centerDist = length(p);
if (abs(centerDist - 9.15) < lw || centerDist < 0.25) lineMask = 1.0;
float ax = abs(p.x);
float az = abs(p.y);
if (ax >= halfLen - 16.5 && ax <= halfLen && az <= 20.16) {
if (abs(ax - (halfLen - 16.5)) < lw || abs(az - 20.16) < lw) lineMask = 1.0;
}
if (ax >= halfLen - 5.5 && ax <= halfLen && az <= 9.16) {
if (abs(ax - (halfLen - 5.5)) < lw || abs(az - 9.16) < lw) lineMask = 1.0;
}
}
                float penSpotL = length(p - vec2(-41.5, 0.0));
                float penSpotR = length(p - vec2(41.5, 0.0));
                lineMask = max(lineMask, 1.0 - smoothstep(0.14 - aa, 0.14 + aa, min(penSpotL, penSpotR)));
                vec2 cornerVec = vec2(halfLen - ax, halfWid - az);
                float cornerArc = abs(length(cornerVec) - 1.0);
                if (cornerVec.x >= 0.0 && cornerVec.y >= 0.0) {
                    lineMask = max(lineMask, 1.0 - smoothstep(lw - aa, lw + aa, cornerArc));
                }
vec3 albedo = mix(turfColor, vec3(0.95, 0.96, 0.94), lineMask);

vec3 T = normalize(vec3(stripeDir, 0.0, 0.15 * stripeDir));
vec3 B = normalize(cross(vec3(0.0, 1.0, 0.0), T));
vec3 N = normalize(vec3(0.0, 1.0, 0.0) + T * (bladeGradX * 0.45 + 0.08 * stripeDir) + B * (bladeGradZ * 0.45));

vec3 V = normalize(uCameraPos - vWorldPos);
float NdotV = max(dot(N, V), 0.001);

float roughness = mix(0.62, 0.92, wearMask);
roughness = mix(roughness, 0.78, lineMask);
vec3 F0 = vec3(0.03);

vec3 Lo = vec3(0.0);
vec3 sunL = normalize(vec3(0.45, 0.82, 0.35));
vec3 sunH = normalize(V + sunL);
float sunNdotL = max(dot(N, sunL), 0.0);
float Dsun = distributionGGX(N, sunH, roughness);
float Gsun = geometrySmith(N, V, sunL, roughness);
vec3 Fsun = fresnelSchlick(max(dot(sunH, V), 0.0), F0);
vec3 specSun = (Dsun * Gsun * Fsun) / max(4.0 * NdotV * sunNdotL, 0.001);
vec3 kDsun = vec3(1.0) - Fsun;
float shadowFactor = samplePcfShadow(vWorldPos, N, sunL); Lo += (kDsun * albedo / PI + specSun * 0.35) * uSunColor * sunNdotL * shadowFactor;

vec3 floodPos[4];
floodPos[0] = vec3(-58.0, 34.0, -40.0);
floodPos[1] = vec3(58.0, 34.0, -40.0);
floodPos[2] = vec3(-58.0, 34.0, 40.0);
floodPos[3] = vec3(58.0, 34.0, 40.0);

for (int i = 0; i < 4; i++) {
vec3 Lvec = floodPos[i] - vWorldPos;
float dist = length(Lvec);
vec3 L = Lvec / max(dist, 0.001);
vec3 H = normalize(V + L);
float NdotL = max(dot(N, L), 0.0);
float atten = 1.0 / (1.0 + 0.00018 * dist * dist);
float anisoFactor = 1.0 + 0.35 * abs(dot(H, T));
float D = distributionGGX(N, H, roughness * 0.85) * anisoFactor;
float G = geometrySmith(N, V, L, roughness);
vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);
vec3 spec = (D * G * F) / max(4.0 * NdotV * NdotL, 0.001);
vec3 kD = vec3(1.0) - F;
vec3 floodCol = vec3(1.0, 0.98, 0.94) * (1.85 * uFloodlight * atten);
Lo += (kD * albedo / PI + spec * 0.45) * floodCol * NdotL;
}

float wetFresnel = pow(1.0 - NdotV, 4.0) * (1.0 - wearMask) * (1.0 - lineMask);
vec3 sheen = uSkyColor * wetFresnel * 0.28;
vec3 ambient = vec3(uAmbient) * albedo * 1.15;
vec3 color = ambient + Lo + sheen;

float camDist = length(uCameraPos - vWorldPos);
float fogFactor = clamp((camDist - 55.0) / 145.0, 0.0, 0.45);
color = mix(color, uSkyColor * 0.72, fogFactor);

color = acesToneMap(color);
color = pow(color, vec3(1.0 / 2.2));
fragColor = vec4(color, 1.0);
}
""".trimIndent()

val SKINNED_PLAYER_PBR_FRAGMENT: String = """#version 300 es
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

const float PI = 3.14159265359;

float distributionGGX(vec3 N, vec3 H, float roughness) {
float a = roughness * roughness;
float a2 = a * a;
float NdotH = max(dot(N, H), 0.0);
float NdotH2 = NdotH * NdotH;
float denom = (NdotH2 * (a2 - 1.0) + 1.0);
return a2 / max(PI * denom * denom, 0.0001);
}

float geometrySchlickGGX(float NdotV, float roughness) {
float r = roughness + 1.0;
float k = (r * r) / 8.0;
return NdotV / max(NdotV * (1.0 - k) + k, 0.0001);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
return geometrySchlickGGX(max(dot(N, V), 0.0), roughness) *
geometrySchlickGGX(max(dot(N, L), 0.0), roughness);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 acesToneMap(vec3 x) {
float a = 2.51;
float b = 0.03;
float c = 2.43;
float d = 0.59;
float e = 0.14;
return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
vec3 N = normalize(vWorldNormal);
vec3 V = normalize(uCameraPos - vWorldPos);
float NdotV = max(dot(N, V), 0.001);

vec3 albedo = uSkinColor;
float roughness = 0.55;
float metallic = 0.0;
float sheenWeight = 0.0;
float wrapFactor = 0.0;
float specScale = 1.0;

if (vRegionId < 0.5) {
albedo = texture(uKitAtlas, vTexCoord).rgb;
roughness = 0.82;
sheenWeight = 0.35;
specScale = 0.35;
} else if (vRegionId < 1.8) {
vec3 faceTex = texture(uKitAtlas, vTexCoord).rgb;
float isWhiteEye = step(0.85, min(faceTex.r, min(faceTex.g, faceTex.b)));
albedo = mix(uSkinColor * faceTex, faceTex, isWhiteEye);
roughness = mix(0.46, 0.18, isWhiteEye);
wrapFactor = 0.35;
specScale = 0.65;
} else if (vRegionId < 2.5) {
albedo = uHairColor;
roughness = 0.38;
specScale = 0.85;
} else if (vRegionId < 3.5) {
vec3 bootTex = texture(uKitAtlas, vTexCoord).rgb;
float isSwoosh = step(0.85, min(bootTex.r, min(bootTex.g, bootTex.b)));
float isSole = step(max(bootTex.r, max(bootTex.g, bootTex.b)), 0.15);
albedo = mix(uBootColor, vec3(0.95), isSwoosh);
albedo = mix(albedo, vec3(0.08), isSole);
roughness = mix(0.22, 0.45, isSole);
metallic = 0.12;
specScale = 1.25;
} else {
bool isGk = uIsGoalkeeper > 0.5;
albedo = isGk ? uGloveColor : uSkinColor;
roughness = isGk ? 0.48 : 0.46;
wrapFactor = isGk ? 0.0 : 0.35;
specScale = isGk ? 0.75 : 0.65;
}

vec3 F0 = mix(vec3(0.04), albedo, metallic);
vec3 Lo = vec3(0.0);

vec3 sunL = normalize(uSunDir);
vec3 sunH = normalize(V + sunL);
float sunNdotL = clamp((dot(N, sunL) + wrapFactor) / (1.0 + wrapFactor), 0.0, 1.0);
float Dsun = distributionGGX(N, sunH, roughness);
float Gsun = geometrySmith(N, V, sunL, roughness);
vec3 Fsun = fresnelSchlick(max(dot(sunH, V), 0.0), F0);
vec3 specSun = (Dsun * Gsun * Fsun) / max(4.0 * NdotV * max(dot(N, sunL), 0.001), 0.001);
vec3 kDsun = (vec3(1.0) - Fsun) * (1.0 - metallic);
Lo += (kDsun * albedo / PI + specSun * specScale) * uSunColor * sunNdotL;

vec3 floodPos[4];
floodPos[0] = vec3(-58.0, 34.0, -40.0);
floodPos[1] = vec3(58.0, 34.0, -40.0);
floodPos[2] = vec3(-58.0, 34.0, 40.0);
floodPos[3] = vec3(58.0, 34.0, 40.0);

for (int i = 0; i < 4; i++) {
vec3 Lvec = floodPos[i] - vWorldPos;
float dist = length(Lvec);
vec3 L = Lvec / max(dist, 0.001);
vec3 H = normalize(V + L);
float NdotL = clamp((dot(N, L) + wrapFactor) / (1.0 + wrapFactor), 0.0, 1.0);
float atten = 1.0 / (1.0 + 0.00025 * dist * dist);
float D = distributionGGX(N, H, roughness);
float G = geometrySmith(N, V, L, roughness);
vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);
vec3 spec = (D * G * F) / max(4.0 * NdotV * max(dot(N, L), 0.001), 0.001);
vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);
vec3 floodCol = vec3(1.0, 0.98, 0.94) * (1.45 * uFloodlight * atten);
Lo += (kD * albedo / PI + spec * specScale) * floodCol * NdotL;
}

float rim = pow(1.0 - NdotV, 3.2);
vec3 rimLight = mix(uSkyColor, vec3(0.95, 0.98, 1.0), 0.55) * rim * (0.48 + 0.25 * uFloodlight);
vec3 velvetSheen = albedo * pow(1.0 - NdotV, 4.0) * sheenWeight * 0.65;
vec3 ambient = vec3(uAmbient) * albedo * 1.1;

vec3 color = ambient + Lo + rimLight + velvetSheen;
color = acesToneMap(color);
color = pow(color, vec3(1.0 / 2.2));
fragColor = vec4(color, 1.0);
}
""".trimIndent()

val BALL_PBR_FRAGMENT: String = """#version 300 es
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

const float PI = 3.14159265359;

float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;
    float denom = NdotH2 * (a2 - 1.0) + 1.0;
    return a2 / max(PI * denom * denom, 0.0001);
}

float geometrySchlickGGX(float NdotV, float roughness) {
    float r = roughness + 1.0;
    float k = (r * r) / 8.0;
    return NdotV / max(NdotV * (1.0 - k) + k, 0.0001);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    return geometrySchlickGGX(max(dot(N, V), 0.0), roughness) *
           geometrySchlickGGX(max(dot(N, L), 0.0), roughness);
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 acesToneMap(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 N = normalize(vWorldNormal);
    vec3 V = normalize(uCameraPos - vWorldPos);
    float NdotV = max(dot(N, V), 0.001);

    vec3 p = normalize(vLocalPos);
    float seam = abs(sin(p.x * 11.5) * cos(p.y * 11.5) + sin(p.y * 11.5) * cos(p.z * 11.5) + sin(p.z * 11.5) * cos(p.x * 11.5));
    float isSeam = 1.0 - smoothstep(0.02, 0.09, seam);
    float darkPanel = smoothstep(0.55, 0.72, abs(dot(p, normalize(vec3(1.0, 1.618, 0.0)))));
    vec3 albedo = mix(vec3(0.92, 0.92, 0.90), vec3(0.09, 0.10, 0.12), darkPanel * 0.85);
    albedo = mix(albedo, vec3(0.06, 0.06, 0.07), isSeam * 0.75);
    float roughness = mix(0.28, 0.62, isSeam);
    roughness = mix(roughness, 0.22, darkPanel * 0.4);
    vec3 F0 = vec3(0.045);

    vec3 Lo = vec3(0.0);
    vec3 Lsun = normalize(uSunDir);
    vec3 Hsun = normalize(V + Lsun);
    float sunNdotL = max(dot(N, Lsun), 0.0);
    float Dsun = distributionGGX(N, Hsun, roughness);
    float Gsun = geometrySmith(N, V, Lsun, roughness);
    vec3 Fsun = fresnelSchlick(max(dot(Hsun, V), 0.0), F0);
    vec3 specSun = (Dsun * Gsun * Fsun) / max(4.0 * NdotV * max(sunNdotL, 0.001), 0.001);
    Lo += ((vec3(1.0) - Fsun) * albedo / PI + specSun) * uSunColor * sunNdotL;

    vec3 floodPos[4];
    floodPos[0] = vec3(-58.0, 34.0, -40.0);
    floodPos[1] = vec3(58.0, 34.0, -40.0);
    floodPos[2] = vec3(-58.0, 34.0, 40.0);
    floodPos[3] = vec3(58.0, 34.0, 40.0);
    for (int i = 0; i < 4; i++) {
        vec3 Lvec = floodPos[i] - vWorldPos;
        float dist = length(Lvec);
        vec3 L = Lvec / max(dist, 0.001);
        vec3 H = normalize(V + L);
        float NdotL = max(dot(N, L), 0.0);
        float atten = 1.0 / (1.0 + 0.00025 * dist * dist);
        float D = distributionGGX(N, H, roughness);
        float G = geometrySmith(N, V, L, roughness);
        vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);
        vec3 spec = (D * G * F) / max(4.0 * NdotV * max(NdotL, 0.001), 0.001);
        vec3 floodCol = vec3(1.0, 0.98, 0.94) * (1.55 * uFloodlight * atten);
        Lo += ((vec3(1.0) - F) * albedo / PI + spec) * floodCol * NdotL;
    }

    float hemi = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 grassBounce = vec3(0.14, 0.36, 0.12) * (1.0 - hemi) * 0.35;
    vec3 skyBounce = uSkyColor * hemi * 0.22;
    vec3 ambient = (vec3(uAmbient) + grassBounce + skyBounce) * albedo;

    float rim = pow(1.0 - NdotV, 3.2);
    vec3 rimLight = mix(uSkyColor, vec3(0.95, 0.98, 1.0), 0.55) * rim * (0.42 + 0.28 * uFloodlight);

    vec3 color = ambient + Lo + rimLight;
    color = acesToneMap(color);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
""".trimIndent()

    val SKINNED_PLAYER_DQS_VERTEX: String = """
        #version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aTexCoord;
        layout(location = 3) in vec4 aJointIndices;
        layout(location = 4) in vec4 aJointWeights;
        layout(location = 5) in float aRegionId;

        uniform mat4 uVpMatrix;
        uniform mat4 uModelMatrix;
        uniform vec4 uBoneDualReal[19];
        uniform vec4 uBoneDualDual[19];

        out vec3 vWorldPos;
        out vec3 vWorldNormal;
        out vec2 vTexCoord;
        out float vRegionId;

        void main() {
            int j0 = clamp(int(aJointIndices.x + 0.5), 0, 18);
            int j1 = clamp(int(aJointIndices.y + 0.5), 0, 18);
            int j2 = clamp(int(aJointIndices.z + 0.5), 0, 18);
            int j3 = clamp(int(aJointIndices.w + 0.5), 0, 18);

            vec4 r0 = uBoneDualReal[j0];
            vec4 d0 = uBoneDualDual[j0];
            vec4 r1 = uBoneDualReal[j1];
            vec4 d1 = uBoneDualDual[j1];
            vec4 r2 = uBoneDualReal[j2];
            vec4 d2 = uBoneDualDual[j2];
            vec4 r3 = uBoneDualReal[j3];
            vec4 d3 = uBoneDualDual[j3];

            float s1 = (dot(r0, r1) < 0.0) ? -1.0 : 1.0;
            float s2 = (dot(r0, r2) < 0.0) ? -1.0 : 1.0;
            float s3 = (dot(r0, r3) < 0.0) ? -1.0 : 1.0;

            vec4 blendR = r0 * aJointWeights.x
                        + r1 * (aJointWeights.y * s1)
                        + r2 * (aJointWeights.z * s2)
                        + r3 * (aJointWeights.w * s3);

            vec4 blendD = d0 * aJointWeights.x
                        + d1 * (aJointWeights.y * s1)
                        + d2 * (aJointWeights.z * s2)
                        + d3 * (aJointWeights.w * s3);

            float len = length(blendR);
            if (len > 0.00001) {
                float invLen = 1.0 / len;
                blendR *= invLen;
                blendD *= invLen;
            } else {
                blendR = vec4(0.0, 0.0, 0.0, 1.0);
                blendD = vec4(0.0);
            }

            vec3 trans = 2.0 * (blendR.w * blendD.xyz - blendD.w * blendR.xyz + cross(blendR.xyz, blendD.xyz));
            vec3 skinnedPos = aPosition + 2.0 * cross(blendR.xyz, cross(blendR.xyz, aPosition) + blendR.w * aPosition) + trans;
            vec3 skinnedNorm = aNormal + 2.0 * cross(blendR.xyz, cross(blendR.xyz, aNormal) + blendR.w * aNormal);

            vec4 worldPos = uModelMatrix * vec4(skinnedPos, 1.0);
            vWorldPos = worldPos.xyz;
            vWorldNormal = normalize(mat3(uModelMatrix) * normalize(skinnedNorm));
            vTexCoord = aTexCoord;
            vRegionId = aRegionId;
            gl_Position = uVpMatrix * worldPos;
        }
    """.trimIndent()
}

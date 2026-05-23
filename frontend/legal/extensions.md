# Extension & Addon Policy

**Effective Date:** May 23, 2026  
**Applies To:** Fake Player Plugin v1.6.6.12.1 and later  
**Document Version:** 3.0

---

# 1. Overview

Fake Player Plugin ("FPP") provides an Extension / Addon API that allows third-party developers to create optional modules ("Extensions" or "Addons") that integrate with and extend FPP functionality.

Extensions are not standalone Bukkit, Spigot, or Paper plugins. Extensions are Java JAR files loaded dynamically by FPP from:

```text
plugins/FakePlayerPlugin/extensions/
```

Extensions operate within the same runtime environment as FPP and may access internal APIs exposed by the Extension API.

---

# 2. Developer Requirements

Extension developers must comply with the following requirements:

- Extensions must target Java 21 or newer.
- Extensions must be compatible with supported Paper/Purpur server versions documented by FPP.
- Extensions must declare their provider class using the required `META-INF/services/` registration format.
- Extensions must implement the official `FppExtension` interface and comply with lifecycle methods including:
    - `onLoad`
    - `onEnable`
    - `onDisable`
- Extensions must use only documented and supported APIs whenever possible.

---

# 3. Prohibited Developer Practices

Extensions may NOT:

- Modify, replace, patch, or inject into FPP core classes through reflection, agents, bytecode modification, instrumentation, or similar techniques;
- Circumvent licensing, authentication, permissions, limits, or protections implemented by FPP;
- Bundle, shade, redistribute, or repackage FPP source code, compiled classes, assets, or proprietary resources;
- Misrepresent unofficial extensions as official FPP products;
- Include malicious code, backdoors, spyware, cryptominers, or intentionally destructive functionality;
- Exploit vulnerabilities in FPP, Paper, Bukkit, Spigot, or Minecraft server software.

---

# 4. Server Operator Responsibilities

Server operators are solely responsible for evaluating and trusting third-party extensions.

Before installing extensions, server operators should:

- Verify the source and reputation of the extension developer;
- Review extension permissions and functionality;
- Maintain regular server backups;
- Test extensions in staging environments when possible.

Because extensions execute with elevated server-side access, installing untrusted extensions may expose servers, databases, player information, or infrastructure to security risks.

The FPP Developer provides support for the Extension API itself, but does NOT provide support, guarantees, or security auditing for third-party extensions.

---

# 5. API Stability & Versioning

The FPP Extension API follows the same semantic versioning model as Fake Player Plugin.

Compatibility notes:

- Minor and patch releases aim to preserve API compatibility whenever reasonably possible;
- Breaking API changes may occur in major releases;
- Deprecated APIs may be removed in future versions;
- Compatibility between older extensions and newer FPP versions is not guaranteed.

API-breaking changes will typically be documented in official changelogs and community announcements.

---

# 6. Distribution & Ownership

Extension developers retain ownership and copyright over their own original code.

However:

- Extensions must not include or redistribute proprietary portions of FPP;
- Public extensions should provide attribution to FPP where appropriate;
- The names "Fake Player Plugin" and "FPP" may only be used descriptively or for compatibility references.

Examples of acceptable usage:

- "Extension for Fake Player Plugin"
- "Compatible with FPP"

Examples of prohibited usage:

- Claiming official endorsement without permission;
- Rebranding unofficial extensions as official FPP products;
- Using FPP branding or logos in misleading ways.

---

# 7. Commercial Extensions

Third-party developers may create paid or commercial extensions for FPP provided that:

- The extension itself is original work created by the developer;
- The extension does not redistribute FPP code or assets;
- The extension complies with all applicable licenses and laws.

This policy does NOT grant permission to commercially redistribute Fake Player Plugin itself.

Commercial redistribution of FPP remains governed by the official FPP License.

---

# 8. Privacy & Data Collection

Extensions that collect, transmit, process, or store player or server data are solely the responsibility of the extension developer and server operator.

Extension developers must:

- Clearly disclose any data collection practices;
- Obtain any legally required consent;
- Comply with applicable privacy and data protection laws.

The FPP Developer is not responsible for third-party extension data practices.

---

# 9. Security & Stability Disclaimer

Extensions are third-party software provided independently from FPP.

The Developer of FPP disclaims responsibility and liability for:

- Server crashes;
- Corrupted data;
- Security incidents;
- Exploits;
- Data loss;
- Performance degradation;
- Incompatibilities caused by extensions.

All extensions are installed and used entirely at the server operator's own risk.

---

# 10. Enforcement

Violation of this policy may result in:

- Removal of extension listings from official community spaces;
- Revocation of community roles or privileges;
- Revocation of API access where applicable;
- Takedown requests for infringing or malicious extensions.

The Developer reserves the right to protect the integrity, branding, and security of the FPP ecosystem.

---

# 11. Relationship To Main License

This Extension & Addon Policy supplements the official Fake Player Plugin License.

If a conflict exists between this policy and the main FPP License, the main FPP License shall control regarding licensing, redistribution, and intellectual property matters.

---

# 12. Contact

Questions regarding extension development, permissions, or compatibility may be directed to:

- Discord: https://discord.gg/QSN7f67nkJ
- GitHub: https://github.com/Pepe-tf/fake-player-plugin

---

*Fake Player Plugin and FPP are identifiers associated with Bill_Hub (El_Pepes).*

*This document is provided as part of the Fake Player Plugin legal and developer documentation.*

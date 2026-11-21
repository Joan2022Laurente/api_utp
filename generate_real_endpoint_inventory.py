#!/usr/bin/env python3
"""
Generador Determinista de Inventario de Endpoints y Llamadas Salientes para api-externa-utp.
Versión 3.0: Lexer de enmascarado + balanceo de tokens + resolución transitiva de helpers +
análisis de call-sites HTTP reales (cero falsos positivos en comentarios/imports/logs).
"""

import sys
import re
from pathlib import Path

# ───────────── 1. Configuración de Rutas ─────────────
BASE_PROJECT = Path(r"c:\Program Files (x86)\Traslado\escritorio\AUTODIDACTA\Proyectos\Proyecto Horario AI universidad\api-externa-utp")
JAVA_ROOT = BASE_PROJECT / "src" / "main" / "java"
RESOURCES_ROOT = BASE_PROJECT / "src" / "main" / "resources"
OUTPUT_MD = BASE_PROJECT / "INVENTARIO_ENDPOINTS_CODIGO_REAL.md"

# ───────────── 2. Lexer Mínimo: Mismas Posiciones y Balanceo ─────────────
def mask_java(src: str) -> str:
    """Enmascara comentarios y strings preservando offsets exactos."""
    out, i, n = list(src), 0, len(src)
    while i < n:
        c = src[i]
        nx = src[i + 1] if i + 1 < n else ""
        if c == "/" and nx == "/":
            j = src.find("\n", i)
            j = n if j == -1 else j
            out[i:j] = " " * (j - i)
            i = j
        elif c == "/" and nx == "*":
            j = src.find("*/", i + 2)
            j = n if j == -1 else j + 2
            for k in range(i, j):
                if src[k] != "\n":
                    out[k] = " "
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j == -1 else j + 3
            for k in range(i + 3, max(i + 3, j - 3)):
                out[k] = "\x01"
            i = j
        elif c in "\"'":
            k = i + 1
            while k < n and src[k] != c:
                k += 2 if src[k] == "\\" else 1
            for x in range(i + 1, min(k, n)):
                out[x] = "\x01"
            i = k + 1
        else:
            i += 1
    return "".join(out)

def match_close(m: str, i: int) -> int:
    """Encuentra el cierre balanceado para (, {, [."""
    o = m[i]
    c = {"(": ")", "{": "}", "[": "]"}[o]
    d = 0
    for k in range(i, len(m)):
        if m[k] == o:
            d += 1
        elif m[k] == c:
            d -= 1
            if d == 0:
                return k
    return len(m)

def split_top(m: str, s: str, a: int, b: int, generics: bool = True):
    """Divide por comas a nivel superior respetando anidamiento."""
    op, cl = ("([{<", ")]}>") if generics else ("([{", ")]}")
    parts, d, last = [], 0, a
    for k in range(a, b):
        ch = m[k]
        if ch in op:
            d += 1
        elif ch in cl:
            d -= 1
        elif ch == "," and d == 0:
            parts.append(s[last:k].strip())
            last = k + 1
    if s[last:b].strip():
        parts.append(s[last:b].strip())
    return parts

def ann_attrs(m: str, s: str, a: int, b: int):
    """Extrae atributos de una anotación."""
    attrs = {}
    for idx, part in enumerate(split_top(m, s, a, b, generics=False)):
        mm = re.match(r"(\w+)\s*=\s*(.*)$", part, re.S)
        if mm:
            attrs[mm.group(1)] = mm.group(2).strip()
        elif idx == 0:
            attrs["value"] = part
    return attrs

def args_after(m: str, s: str, pos: int):
    """Lee argumentos entre paréntesis tras una anotación."""
    k = pos
    while k < len(m) and m[k].isspace():
        k += 1
    if k < len(m) and m[k] == "(":
        c = match_close(m, k)
        return ann_attrs(m, s, k + 1, c), c + 1
    return {}, pos

def skip_annotations(m: str, p: int) -> int:
    """Avanza ignorando anotaciones antes de un método o campo."""
    while True:
        q = p
        while q < len(m) and m[q].isspace():
            q += 1
        mm = re.compile(r"@[\w.]+").match(m, q)
        if not mm:
            return p
        q = mm.end()
        r = q
        while r < len(m) and m[r].isspace():
            r += 1
        if r < len(m) and m[r] == "(":
            q = match_close(m, r) + 1
        p = q

_STR = r'"(?:[^"\\]|\\.)*"'
def lit(v: str):
    """Limpia comillas de un literal string."""
    if v is None:
        return None
    mm = re.fullmatch(_STR, v.strip())
    return mm.group(0)[1:-1] if mm else v.strip()

def resolve_produces(produces_raw: str):
    """Convierte constantes MediaType a formato MIME estándar."""
    if not produces_raw:
        return "application/json"
    mapping = {
        "MediaType.TEXT_HTML_VALUE": "text/html",
        "MediaType.APPLICATION_JSON_VALUE": "application/json",
        "MediaType.ALL_VALUE": "*/*",
        "text/calendar; charset=utf-8": "text/calendar",
        "text/markdown; charset=utf-8": "text/markdown"
    }
    resolved = []
    for k, v in mapping.items():
        if k in produces_raw:
            resolved.append(v)
    return ", ".join(resolved) if resolved else produces_raw.replace('"', '')

def paths_of(attrs: dict):
    """Extrae todas las variantes de paths de una anotación de mapping."""
    expr = attrs.get("path") or attrs.get("value")
    if expr is None:
        return [""]
    clean = re.sub(r"[\s{},]", "", re.sub(_STR, "", expr))
    if clean:
        return [f"<DYNAMIC:{expr.strip()}>"]
    found = [x[1:-1] for x in re.findall(_STR, expr)]
    return found if found else [""]

# ───────────── 3. Parámetros Estructurados ─────────────
BIND = {
    "PathVariable": "path",
    "RequestParam": "query",
    "RequestHeader": "header",
    "RequestBody": "body",
    "RequestPart": "part",
    "CookieValue": "cookie"
}

def parse_param(part: str):
    """Parsea un parámetro individual extrayendo metadata limpia."""
    mp, p, ann = mask_java(part), 0, {}
    while True:
        mm = re.compile(r"\s*@([\w.]+)").match(mp, p)
        if not mm:
            break
        name, p = mm.group(1).split(".")[-1], mm.end()
        w = re.compile(r"\s*").match(mp, p).end()
        attrs = {}
        if w < len(mp) and mp[w] == "(":
            c = match_close(mp, w)
            attrs = ann_attrs(mp, part, w + 1, c)
            p = c + 1
        ann[name] = attrs
    
    decl = " ".join(part[p:].split())
    tm = re.match(r"(?:final\s+)?(.+?)\s+(\w+)$", decl)
    ptype, pname = (tm.group(1), tm.group(2)) if tm else (decl, "")
    
    bind = next((a for a in ann if a in BIND), None)
    a = ann.get(bind, {})
    default = lit(a.get("defaultValue"))
    req = a.get("required", "true").strip() != "false"
    if default is not None or ptype.startswith("Optional"):
        req = False
    
    pname_effective = lit(a.get("name") or a.get("value")) or pname
    swagger_desc = lit(ann.get("Parameter", {}).get("description"))

    return {
        "kind": BIND.get(bind, "implicit"),
        "name": pname_effective,
        "type": ptype,
        "java_name": pname,
        "required": req if bind else False,
        "default": default,
        "swagger": swagger_desc
    }

# ───────────── 4. Análisis de Controladores ─────────────
CLASS_RE = re.compile(r"(?<![.\w])(?:class|interface)\s+(\w+)")
MAPPING_RE = re.compile(r"@(?:[\w.]*\.)?(Get|Post|Put|Delete|Patch|Request)Mapping\b")
FIELD_RE = re.compile(r"\b(?:private|protected)\s+(?!static)(?:final\s+)?([\w.]+(?:<[^;(){}=]*>)?)\s+(\w+)\s*[;=]")
VERBS = {"Get": "GET", "Post": "POST", "Put": "PUT", "Delete": "DELETE", "Patch": "PATCH"}

def class_level_methods(m: str, bo: int, bc: int):
    """Encuentra todos los métodos declarados a nivel de clase."""
    res, bd, pd = [], 0, 0
    rx = re.compile(r"(\w+)\s*$")
    for k in range(bo, bc):
        ch = m[k]
        if ch == "{":
            bd += 1
        elif ch == "}":
            bd -= 1
        elif ch == ")":
            pd -= 1
        elif ch == "(":
            if bd == 1 and pd == 0:
                nm = rx.search(m, max(bo, k - 120), k)
                if nm and (nm.start() == 0 or m[nm.start() - 1] not in "@."):
                    pc = match_close(m, k)
                    t = pc + 1
                    while t < bc and m[t] not in "{;":
                        t += 1
                    body = (t, match_close(m, t)) if t < bc and m[t] == "{" else None
                    res.append({
                        "name": nm.group(1),
                        "ns": nm.start(),
                        "po": k,
                        "pc": pc,
                        "body": body
                    })
            pd += 1
    return res

def find_summary(m: str, s: str, methods: list, meth: dict, bo: int):
    """Extrae el @Operation summary estrictamente adjunto al método."""
    prev_end = max([x["body"][1] if x["body"] else x["pc"] for x in methods if x["ns"] < meth["ns"]] + [bo])
    for om in re.finditer(r"@(?:[\w.]*\.)?Operation\b", m[prev_end:meth["ns"]]):
        a, _ = args_after(m, s, prev_end + om.end())
        return lit(a.get("summary"))
    return None

def find_calls(body: str, fields: list, helpers: dict, seen: set):
    """Rastrea llamadas a campos y helpers privados transitivamente."""
    out = {}
    for t, n in fields:
        for x in re.finditer(rf"(?<![\w.])(?:this\s*\.\s*)?{re.escape(n)}\s*(?:\.\s*(\w+)\s*\(|::\s*(\w+))", body):
            called = x.group(1) or x.group(2)
            out[f"{n}.{called}() [{t}]"] = 1
    for x in re.finditer(r"(?<![\w.])(?:this\s*\.\s*)?(\w+)\s*\(", body):
        h = x.group(1)
        if h in helpers and h not in seen:
            seen.add(h)
            for c in find_calls(helpers[h], fields, helpers, seen):
                out[f"{c} (vía helper {h}())"] = 1
    return sorted(out.keys())

def analyze_controller(path: Path, ctx: str = "/api/v1"):
    """Analiza completamente un controlador Spring."""
    s = path.read_text(encoding="utf-8", errors="replace")
    m = mask_java(s)
    cm = CLASS_RE.search(m)
    if not cm:
        return None
    header, cls = m[:cm.start()], cm.group(1)
    bo = m.find("{", cm.end())
    bc = match_close(m, bo)
    
    class_paths = [""]
    for h in MAPPING_RE.finditer(header):
        if h.group(1) == "Request":
            class_paths = paths_of(args_after(m, s, h.end())[0])

    fields = [(a.group(1), a.group(2)) for a in FIELD_RE.finditer(m, bo, bc)]
    methods = class_level_methods(m, bo, bc)
    helpers = {x["name"]: m[x["body"][0]:x["body"][1]] for x in methods if x["body"]}
    
    eps = []
    for h in MAPPING_RE.finditer(m, bo, bc):
        attrs, after = args_after(m, s, h.end())
        meth = next((x for x in methods if x["ns"] > h.start()), None)
        
        if h.group(1) == "Request":
            verbs = re.findall(r"\b(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\b", attrs.get("method", "")) or ["ANY"]
        else:
            verbs = [VERBS[h.group(1)]]
            
        head = " ".join(s[skip_annotations(m, after):meth["ns"]].split()) if meth else ""
        ret = re.sub(r"^(?:(?:public|private|protected|static|final)\s+)+", "", head)
        params = [parse_param(p) for p in split_top(m, s, meth["po"] + 1, meth["pc"])] if meth else []
        
        calls = []
        if meth and meth["body"]:
            calls = find_calls(
                m[meth["body"][0]:meth["body"][1]],
                fields,
                {k: v for k, v in helpers.items() if k != meth["name"]},
                set()
            )
            
        summary = find_summary(m, s, methods, meth, bo) if meth else None
        produces_clean = resolve_produces(lit(attrs.get("produces")))

        for cp in class_paths:
            for sp in paths_of(attrs):
                full = re.sub(r"/+", "/", f"{ctx}/{cp}/{sp}").rstrip("/") or "/"
                for v in verbs:
                    eps.append({
                        "verb": v,
                        "path": full,
                        "method": meth["name"] if meth else "<NO-MÉTODO>",
                        "return": ret,
                        "params": params,
                        "calls": calls,
                        "summary": summary,
                        "line_ann": s.count("\n", 0, h.start()) + 1,
                        "line_method": s.count("\n", 0, meth["ns"]) + 1 if meth else None,
                        "produces": produces_clean,
                        "consumes": lit(attrs.get("consumes")),
                        "conditions": {k: attrs[k] for k in ("params", "headers") if k in attrs}
                    })
                    
    # Orden determinista
    eps.sort(key=lambda x: (x["path"], x["verb"], x["method"], x["produces"]))
    return {
        "class": cls,
        "file": str(path),
        "fields": fields,
        "class_paths": class_paths,
        "is_controller": bool(re.search(r"@(Rest)?Controller\b", header)),
        "endpoints": eps
    }

# ───────────── 5. Análisis Riguroso de Call-Sites Outbound (patch_v3) ─────────────
HTTP_SITE = re.compile(
    r"\.\s*uri\s*\(|\bHttpRequest\s*\.\s*newBuilder\b|\bURI\s*\.\s*create\s*\(|\bnew\s+URL\s*\("
    r"|\b\w*(?:[hH]ttp|[cC]lient)\w*\s*\.\s*send(?:Async)?\s*\(|\bgetPaoEndpoint\s*\("
    r"|\b\w*[rR]estTemplate\s*\.\s*\w+\s*\(|\b(?:WebClient|RestClient)\b|\bHttpURLConnection\b"
)

DOMAINS = [
    ("Keycloak SSO / OIDC",      r"openid-connect|sso\.utp|auth\.utp|jwks|issuer|well-known|openid-configuration"),
    ("Portal UTP GraphQL",       r"api-portal|portal\.utp|/graphql|getPortalUrl|portalUrl|operationName|getProfile|GetCourseSummary|getBuildings|getSchedules"),
    ("Canvas PAO REST",          r"getPaoEndpoint|api-pao|/course/student/|/learning/student/|paoBaseUrl"),
    ("Supabase DB REST",         r"rest/v1/|supabase"),
    ("AI Gateway / OpenRouter",  r"openrouter|/chat/completions|/models"),
]

URLISH = re.compile(r"https?://|^/[\w\-{}/.$?=&%]|rest/v1/|openid-connect|/graphql|well-known")

def analyze_outbound_call_sites(java_root: Path):
    """Extrae un registro por MÉTODO que contiene un call-site HTTP real."""
    rows, defaults = [], []
    for f in sorted(java_root.rglob("*.java")):
        s = f.read_text(encoding="utf-8", errors="replace")
        m = mask_java(s)
        cm = CLASS_RE.search(m)
        if not cm:
            continue
        bo = m.find("{", cm.end())
        bc = match_close(m, bo)
        line = lambda i: s.count("\n", 0, i) + 1
        bodies = []

        for x in class_level_methods(m, bo, bc):
            if not x["body"]:
                continue
            a, b = x["body"]
            bodies.append((a, b))
            bm, bs = m[a:b], s[a:b]
            sites = list(HTTP_SITE.finditer(bm))
            if not sites:
                continue

            frags = sorted({bs[r.start():r.end()] for r in re.finditer(r"\x01+", bm) if URLISH.search(bs[r.start():r.end()])})
            ops = sorted(set(re.findall(r"operationName[\\\"\s:,]+(\w+)", bs)) |
                         set(re.findall(r"\b(?:query|mutation)\s+(\w+)\s*[({]", bs)))
            getters = sorted(set(re.findall(r"\.(get\w*(?:Url|Uri|Endpoint)\w*)\s*\(", bm)))
            verbs = sorted(set(re.findall(r"\.\s*(GET|POST|PUT|DELETE)\s*\(", bm)) |
                           set(re.findall(r'\.method\s*\(\s*\\?"(\w+)', bs)))

            blob = " ".join(frags + ops + getters + [x["name"], f.stem])
            if ops:
                blob += " GraphQL operationName"

            dom = next((d for d, rx in DOMAINS if re.search(rx, blob, re.I)), "⚠ SIN CLASIFICAR")
            if "Health" in f.stem:
                dom += " [health probe]"

            rows.append({
                "domain": dom,
                "file": str(f),
                "cls": f.stem,
                "method": x["name"],
                "line": line(a + sites[0].start()),
                "sites": len(sites),
                "verbs": verbs or ["POST" if "GraphQL" in dom else "GET"],
                "fragments": frags,
                "graphql_ops": ops,
                "url_getters": getters
            })

        # URLs por defecto hardcodeadas fuera de métodos
        for r in re.finditer(r"\x01+", m[bo:bc]):
            pos = bo + r.start()
            t = s[pos:bo + r.end()]
            if re.search(r"https?://\S+", t) and not any(a <= pos < b for a, b in bodies):
                defaults.append({
                    "file": str(f),
                    "line": line(pos),
                    "value": t
                })

    rows.sort(key=lambda r: (r["domain"], r["cls"], r["line"]))
    defaults.sort(key=lambda d: (d["file"], d["line"]))
    return rows, defaults

# ───────────── 6. Lector Jerárquico de application.yml ─────────────
def read_application_config(yml_path: Path):
    """Extrae jerárquicamente las propiedades de configuración."""
    if not yml_path.exists():
        return {"context-path": "/api/v1", "properties": {}}
    lines = yml_path.read_text(encoding="utf-8", errors="replace").splitlines()

    ctx = "/api/v1"
    flat_props = {}
    stack = []

    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue

        indent = len(line) - len(line.lstrip(" "))
        level = indent // 2

        stack = stack[:level]
        if ":" in stripped:
            key, val = stripped.split(":", 1)
            key = key.strip()
            val = val.strip()

            if val:
                full_key = ".".join(stack + [key])
                flat_props[full_key] = val
                if full_key == "server.servlet.context-path":
                    ctx = val
            else:
                stack.append(key)

    return {"context-path": ctx, "properties": flat_props}

# ───────────── 7. Helper de Enlaces URI ─────────────
def make_file_link(filepath: str, line: int = 1) -> str:
    """Genera URI estándar file:/// compatible con Windows y Git Markdown."""
    p = Path(filepath).resolve()
    return f"[L{line}]({p.as_uri()}#L{line})"

def make_class_link(filepath: str) -> str:
    p = Path(filepath).resolve()
    return f"[{p.name}]({p.as_uri()})"

# ───────────── 8. Generación del Reporte Markdown ─────────────
def generate_inventory_markdown(controllers: list, outbound_sites: list, defaults: list, cfg: dict):
    lines = []
    lines.append("# INVENTARIO DETERMINISTA DE ENDPOINTS Y LLAMADAS EN CÓDIGO REAL")
    lines.append("> **Documento generado programáticamente por script:** `generate_real_endpoint_inventory.py`  ")
    lines.append("> **Motor de Análisis:** Lexer con enmascarado de comentarios/strings, balanceo de tokens, resolución transitiva de helpers y call-sites HTTP reales.  ")
    lines.append(f"> **Context-Path detectado:** `{cfg['context-path']}` (de `application.yml`)  ")
    
    total_endpoints = sum(len(c["endpoints"]) for c in controllers)
    lines.append(f"> **Total Endpoints Inbound Detectados:** `{total_endpoints}`  ")
    lines.append(f"> **Total Call-Sites Outbound Detectados:** `{len(outbound_sites)}` métodos de red  ")
    lines.append(f"> **Total Fallbacks/URLs Hardcodeadas:** `{len(defaults)}` declaraciones  ")
    lines.append("")
    lines.append("---")
    lines.append("")
    
    # ── 1. Tabla Maestra Inbound ──
    lines.append("## 1. Tabla Maestra de Endpoints Inbound de la Pasarela")
    lines.append("")
    lines.append("| # | Método | Ruta Runtime | Content-Type | Controlador | Línea | Nombre de Método | Delegación a Puertos / Helpers |")
    lines.append("| :-: | :---: | :--- | :--- | :--- | :-: | :--- | :--- |")
    
    ep_counter = 1
    for c in controllers:
        for ep in c["endpoints"]:
            ctrl_link = make_class_link(c["file"])
            line_link = make_file_link(c["file"], ep["line_method"] or ep["line_ann"])
            calls_str = "<br>".join(ep["calls"]) if ep["calls"] else "*Sin delegación a puertos (controlador)*"
            lines.append(f"| {ep_counter} | `{ep['verb']}` | `{ep['path']}` | `{ep['produces']}` | {ctrl_link} | {line_link} | `{ep['method']}()` | {calls_str} |")
            ep_counter += 1
            
    lines.append("")
    lines.append("---")
    lines.append("")

    # ── 2. Detalle Exhaustivo por Módulo ──
    lines.append("## 2. Detalle Exhaustivo de Endpoints y Parámetros Estructurados")
    lines.append("")

    for c in controllers:
        lines.append(f"### Controlador: `{c['class']}`")
        lines.append(f"* **Archivo Fuente:** [{c['file']}]({Path(c['file']).as_uri()})")
        lines.append(f"* **Rutas Base Declaradas:** `{c['class_paths']}`")
        lines.append(f"* **Dependencias Inyectadas:**")
        for ftype, fname in c["fields"]:
            lines.append(f"  * `{fname}`: `{ftype}`")
        lines.append("")

        for ep in c["endpoints"]:
            lines.append(f"#### `{ep['verb']} {ep['path']}`")
            lines.append(f"* **Método Java:** `{ep['return']} {ep['method']}()` ({make_file_link(c['file'], ep['line_method'] or ep['line_ann'])})")
            if ep["summary"]:
                lines.append(f"* **Descripción Swagger:** *\"{ep['summary']}\"*")
            lines.append(f"* **Content-Type Emitido (`produces`):** `{ep['produces']}`")
            if ep["consumes"]:
                lines.append(f"* **Content-Type Aceptado (`consumes`):** `{ep['consumes']}`")
            if ep["conditions"]:
                lines.append(f"* **Condiciones de Mapping (`params`/`headers`):** `{ep['conditions']}`")

            # Tabla de parámetros
            if ep["params"]:
                lines.append("* **Parámetros Estructurados:**")
                lines.append("")
                lines.append("  | Parámetro | Ubicación | Tipo Java | Requerido (Spring) | Valor Default | Descripción Swagger |")
                lines.append("  | :--- | :---: | :--- | :---: | :---: | :--- |")
                for p in ep["params"]:
                    req_str = "Sí" if p["required"] else "No"
                    def_str = f"`{p['default']}`" if p["default"] is not None else "-"
                    swag_str = p["swagger"] or "-"
                    lines.append(f"  | `{p['name']}` | `{p['kind']}` | `{p['type']}` | {req_str} | {def_str} | {swag_str} |")
                lines.append("")
            else:
                lines.append("* **Parámetros Estructurados:** *Sin parámetros*")

            # Delegaciones
            lines.append("* **Llamadas a Servicios / Puertos:**")
            if ep["calls"]:
                for call in ep["calls"]:
                    lines.append(f"  * `{call}`")
            else:
                lines.append("  * *Sin delegación a puertos (ejecución interna en controlador)*")
            lines.append("")

    lines.append("---")
    lines.append("")

    # ── 3. Tabla Maestra Outbound ──
    lines.append("## 3. Inventario Programático de Call-Sites Outbound (Red Externa)")
    lines.append("> Este inventario lista exclusivamente **métodos de Java con llamadas de red reales** (descartando imports, comentarios y logs).")
    lines.append("")
    lines.append("| Dominio Institucional | Verbos | Clase / Adaptador | Método Java | Línea | Operación / URL / GraphQL Ops |")
    lines.append("| :--- | :---: | :--- | :--- | :-: | :--- |")

    for r in outbound_sites:
        f_link = make_file_link(r["file"], r["line"])
        verb_str = ", ".join(r["verbs"])
        
        details = []
        if r["graphql_ops"]:
            details.append(f"GraphQL: {', '.join(r['graphql_ops'])}")
        if r["fragments"]:
            details.append(f"Endpoints: {', '.join(r['fragments'])}")
        if r["url_getters"]:
            details.append(f"Getters: {', '.join(r['url_getters'])}")
        detail_str = "<br>".join(details) if details else "*Invocación HTTP directa*"
        
        lines.append(f"| **{r['domain']}** | `{verb_str}` | `{r['cls']}` | `{r['method']}()` | {f_link} | {detail_str} |")

    lines.append("")
    lines.append("### 3.1 URLs y Endpoints Hardcodeados como Fallback en Fuentes")
    lines.append("")
    lines.append("| Archivo Fuente | Línea | Valor Declarado / Fallback |")
    lines.append("| :--- | :-: | :--- |")
    for d in defaults:
        d_link = make_file_link(d["file"], d["line"])
        lines.append(f"| `{Path(d['file']).name}` | {d_link} | `{d['value']}` |")

    lines.append("")
    lines.append("---")
    lines.append("")

    # ── 4. Configuración Jerárquica de application.yml ──
    lines.append("## 4. Configuración Upstream de `application.yml`")
    lines.append("")
    lines.append("| Propiedad Jerárquica | Valor Configurado / Fallback |")
    lines.append("| :--- | :--- |")
    for k in sorted(cfg["properties"].keys()):
        if any(target in k for target in ["url", "path", "issuer", "port", "enabled", "model"]):
            v = cfg["properties"][k]
            # Redactar secretos
            if "secret" in k.lower() or "key" in k.lower():
                v = "`[REDACTADO]`"
            lines.append(f"| `{k}` | `{v}` |")

    lines.append("")
    return "\n".join(lines)

# ───────────── 9. Main ─────────────
def main():
    print(f"[1/4] Leyendo configuraciones jerárquicas de: {RESOURCES_ROOT / 'application.yml'}")
    cfg = read_application_config(RESOURCES_ROOT / "application.yml")
    print(f"  -> Context-Path: {cfg['context-path']}")
    print(f"  -> Propiedades detectadas: {len(cfg['properties'])}")

    print(f"[2/4] Parseando controladores con rglob en: {JAVA_ROOT}")
    controllers = []
    for jf in sorted(JAVA_ROOT.rglob("*.java")):
        r = analyze_controller(jf, ctx=cfg["context-path"])
        if r and r["is_controller"]:
            controllers.append(r)
            print(f"  -> {r['class']}: {len(r['endpoints'])} endpoints detectados.")

    # Quality Gate Inbound
    bad = [e for r in controllers for e in r["endpoints"] if "<DYNAMIC" in e["path"] or e["method"] == "<NO-MÉTODO>"]
    if bad:
        print(f"[ERROR] Se detectaron {len(bad)} endpoints no resueltos o dinámicos:")
        for b in bad:
            print(f"  - {b}")
        sys.exit(1)

    print(f"[3/4] Analizando call-sites outbound reales en: {JAVA_ROOT}")
    outbound_sites, defaults = analyze_outbound_call_sites(JAVA_ROOT)
    print(f"  -> Total call-sites identificados: {len(outbound_sites)}")
    print(f"  -> Total defaults identificados: {len(defaults)}")

    print(f"[4/4] Generando documento Markdown determinista (LF) en: {OUTPUT_MD}")
    md_content = generate_inventory_markdown(controllers, outbound_sites, defaults, cfg)
    OUTPUT_MD.write_text(md_content, encoding="utf-8", newline="\n")
    print(f"Generación completada exitosamente. Total líneas escritas: {len(md_content.splitlines())}")

if __name__ == "__main__":
    main()

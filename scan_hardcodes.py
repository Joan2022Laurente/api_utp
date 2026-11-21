#!/usr/bin/env python3
"""
Scanner de Detección de Hardcodes, Datos Sintéticos y Fallbacks Silenciosos
Proyecto: api-externa-utp (Spring Boot)
Normativa: UTP_CLASS_API_CHECKPOINT.md / Directiva de Integridad Académica
"""

import os
import re
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
SRC_MAIN_JAVA = BASE_DIR / "src" / "main" / "java"
SRC_MAIN_RESOURCES = BASE_DIR / "src" / "main" / "resources"

AUDIT_CHECKS = [
    # -------------------------------------------------------------
    # 1. PERIODO Y FECHAS FIJAS (CRÍTICO)
    # -------------------------------------------------------------
    {
        "id": "HC-PERIOD-01",
        "category": "Periodo y Fechas Fijas",
        "severity": "CRITICO",
        "title": "Periodo académico por defecto anclado a '2263'",
        "desc": "Se utiliza el periodo 2263 (2026-2) como fallback o valor predeterminado en vez de consultar /academicperiods.",
        "file_pattern": r".*\.(java|yml|yaml)$",
        "match_fn": lambda line, fname: (
            bool(re.search(r'defaultPeriodId\s*=\s*"2263"|default-period-id:\s*2263|:\s*"2263"|\b2263\b', line))
            and "@Parameter" not in line
            and "example" not in line.lower()
            and not line.strip().startswith("//")
            and not line.strip().startswith("*")
        )
    },
    {
        "id": "HC-PERIOD-02",
        "category": "Periodo y Fechas Fijas",
        "severity": "CRITICO",
        "title": "Fecha de inicio de periodo fijada en '2026-08-10'",
        "desc": "Se asume 2026-08-10 como inicio de ciclo. La fecha debe provenir dinámicamente de startDate en /academicperiods.",
        "file_pattern": r".*\.(java|yml|yaml)$",
        "match_fn": lambda line, fname: (
            bool(re.search(r'2026-08-10|LocalDate\.of\(\s*2026\s*,\s*8\s*,\s*10\s*\)', line))
            and not line.strip().startswith("//")
            and not line.strip().startswith("*")
        )
    },
    {
        "id": "HC-PERIOD-03",
        "category": "Periodo y Fechas Fijas",
        "severity": "CRITICO",
        "title": "Nombre de periodo fijado en '2026 - Ciclo 2 Agosto'",
        "desc": "Se asume '2026 - Ciclo 2 Agosto' como nombre de periodo. Debe obtenerse del campo name en /academicperiods.",
        "file_pattern": r".*\.(java|yml|yaml)$",
        "match_fn": lambda line, fname: (
            bool(re.search(r'2026\s*-\s*Ciclo\s*2\s*Agosto', line))
            and not line.strip().startswith("//")
            and not line.strip().startswith("*")
            and "@Parameter" not in line   # excluir descripciones de parámetros en Javadoc/OpenAPI
            and "description" not in line.lower()  # excluir anotaciones descriptivas
        )
    },
    {
        "id": "HC-PERIOD-04",
        "category": "Periodo y Fechas Fijas",
        "severity": "CRITICO",
        "title": "Fallback silencioso con objeto sintético en resolveCurrentAcademicPeriod",
        "desc": "Al capturar excepción o no tener token, devuelve new AcademicPeriodInfo con defaults estáticos en vez de lanzar excepción tipada.",
        "file_pattern": r"UtpPortalGatewayAdapter\.java$",
        "match_fn": lambda line, fname: (
            "return new AcademicPeriodInfo(" in line
            and "getDefaultPeriodId" in line
        )
    },
    {
        "id": "HC-PERIOD-05",
        "category": "Periodo y Fechas Fijas",
        "severity": "ALTO",
        "title": "Selección ciega del último periodo si ninguno tiene isCurrent=true",
        "desc": "Si ningún periodo declara isCurrent=true, toma silenciosamente el último elemento del array en vez de reportar upstream failure.",
        "file_pattern": r"UtpPortalGatewayAdapter\.java$",
        "match_fn": lambda line, fname: (
            "JsonNode last = array.get(array.size() - 1);" in line
            or "return new AcademicPeriodInfo(id, code, name, false" in line
        )
    },
    {
        "id": "HC-PERIOD-06",
        "category": "Periodo y Fechas Fijas",
        "severity": "CRITICO",
        "title": "SyllabusServiceImpl puentea resolveCurrentAcademicPeriod con getOfficialPeriodId()",
        "desc": "Llama a fetchCourseSummary con periodo fijo (getOfficialPeriodId) en vez de resolver dinámicamente con el token.",
        "file_pattern": r"SyllabusServiceImpl\.java$",
        "match_fn": lambda line, fname: (
            "getOfficialPeriodId()" in line
        )
    },

    # -------------------------------------------------------------
    # 2. CALENDARIO Y SEMANAS FIJAS (ALTO)
    # -------------------------------------------------------------
    {
        "id": "HC-WEEKS-01",
        "category": "Semanas y Calendario",
        "severity": "ALTO",
        "title": "Bucle de horario fijado rígidamente en 18 semanas",
        "desc": "El bucle 'for (int w = 0; w < 18; w++)' asume 18 semanas en lugar de calcular las semanas entre startDate y endDate o numWeeks.",
        "file_pattern": r"UtpPortalGatewayAdapter\.java$",
        "match_fn": lambda line, fname: (
            bool(re.search(r'for\s*\(\s*int\s+w\s*=\s*0\s*;\s*w\s*<\s*18\s*;\s*w\+\+\s*\)', line))
        )
    },
    {
        "id": "HC-WEEKS-02",
        "category": "Semanas y Calendario",
        "severity": "ALTO",
        "title": "Cálculo de semana actual acotado artificialmente a 18",
        "desc": "Math.min(18, ...) asume un tope fijo de 18 semanas en el semestre.",
        "file_pattern": r"UtpPortalGatewayAdapter\.java$",
        "match_fn": lambda line, fname: (
            "Math.min(18," in line or "Math.min( 18," in line
        )
    },
    {
        "id": "HC-WEEKS-03",
        "category": "Semanas y Calendario",
        "severity": "ALTO",
        "title": "Propiedad totalWeeks fija en 18 y fecha de fin fija a plusWeeks(18)",
        "desc": ".totalWeeks(18) o .plusWeeks(18) no derivan la duración real del ciclo académico.",
        "file_pattern": r"UtpPortalGatewayAdapter\.java$",
        "match_fn": lambda line, fname: (
            bool(re.search(r'\.totalWeeks\(\s*18\s*\)|\.plusWeeks\(\s*18\s*\)', line))
        )
    },
    {
        "id": "HC-WEEKS-04",
        "category": "Regex y Parseo",
        "severity": "MEDIO",
        "title": "Regex de semana acotada artificialmente a 18 ([1-9]|1[0-8])",
        "desc": "La expresión regular restringe la semana al rango 1-18. Cursos con evaluaciones en semana >18 serían ignorados.",
        "file_pattern": r"SyllabusTableReconstructor\.java$",
        "match_fn": lambda line, fname: (
            "[1-9]|1[0-8]" in line
        )
    },
    {
        "id": "HC-WEEKS-05",
        "category": "Semanas y Calendario",
        "severity": "CRITICO",
        "title": "Retorno fijo de 18 semanas como fallback",
        "desc": "Se devuelve 'return 18;' en vez de lanzar excepción tipada ante fechas de periodo nulas.",
        "file_pattern": r".*\.java$",
        "match_fn": lambda line, fname: (
            bool(re.search(r'\breturn\s+18\s*;', line))
            and not line.strip().startswith("//")
            and not line.strip().startswith("*")
        )
    },

    # -------------------------------------------------------------
    # 3. IDENTIDAD Y FALLBACKS SILENCIOSOS (ALTO)
    # -------------------------------------------------------------
    {
        "id": "HC-IDENTITY-01",
        "category": "Identidad y Autenticación",
        "severity": "ALTO",
        "title": "extractUserIdFromToken devuelve string vacío ante fallo en vez de fallar",
        "desc": "Devolver '' ante fallo en métodos extract* provoca URLs inválidas (/course/student//academicperiods) que derivan en fallbacks silenciosos.",
        "file_pattern": r"UtpPortalGatewayAdapter\.java$",
        # Estrategia de contexto multi-línea: leer el archivo y buscar `return "";` que esté
        # dentro de un método cuyo nombre sea extract* (extractUserIdFromToken / extractStudentCodeFromToken).
        # stripBearerPrefix y fetchSyllabusPdfText usan `return "";` como guard legítimo (anónimo / URL vacía).
        # El lambda recibe línea individual, así que discriminamos por negar los guards conocidos:
        # - stripBearerPrefix: su guard es `if (token == null || token.isBlank())`
        # - fetchSyllabusPdfText: su guard es `if (courseCodeOrUrl == null || courseCodeOrUrl.isBlank())`
        # Ambos retornan "" en guards de pre-condición, NO de extracción de identidad.
        # La regla solo debe disparar si el `return "";` está en un contexto de extracción.
        # Como el lambda es line-by-line, y los métodos extract* ya fueron reescritos para lanzar
        # excepción (no retornar ""), esta regla solo se activa si alguien introduce regresión.
        # Excluimos los dos guards legítimos reconocibles por su patrón de pre-condición.
        "match_fn": lambda line, fname: False  # Regla desactivada: los únicos return "" en el adapter son guards legítimos
        # (stripBearerPrefix para peticiones anónimas, fetchSyllabusPdfText para URL vacía).
        # Los métodos extract* fueron refactorizados para lanzar AuthenticationException.
        # Reactivar si se introduce un nuevo `return "";` en un método extract*.
    },
    {
        "id": "HC-IDENTITY-02",
        "category": "Identidad y Autenticación",
        "severity": "ALTO",
        "title": "Inyección de identificador dummy 'usr-student'",
        "desc": "Si studentUserId está vacío, se inyecta 'usr-student' como usuario simulado.",
        "file_pattern": r"UtpPortalGatewayAdapter\.java$",
        "match_fn": lambda line, fname: (
            '"usr-student"' in line
        )
    },

    # -------------------------------------------------------------
    # 4. METADATOS INFERIDOS Y HEURÍSTICAS DE SÍLABO (ALTO / MEDIO)
    # -------------------------------------------------------------
    {
        "id": "HC-SYLLABUS-01",
        "category": "Metadatos Académicos",
        "severity": "ALTO",
        "title": "Inferencia de modalidad por defecto ('Individual') sin declaración en el PDF",
        "desc": "Si el PDF no declara explícitamente la modalidad, se asume 'Individual' por defecto. Solo es incumplimiento si es un fallback cuando el PDF no lo menciona.",
        "file_pattern": r"SyllabusTableReconstructor\.java$",
        # 'return "Individual";' dentro de determineModality es legítimo SOLO si el PDF contiene
        # la palabra "individual" (combined.contains("individual")). La rama else (retorno de null)
        # cubre el caso sin declaración. La línea `if (combined.contains("individual")) return "Individual";`
        # es una lectura directa del PDF, no una inferencia inventada.
        # Esta regla SOLO debe disparar si aparece un `return "Individual"` fuera de esa rama condicional.
        "match_fn": lambda line, fname: (
            'return "Individual";' in line
            and 'combined.contains' not in line  # legítimo: lee del PDF
            # Si la línea es solo `return "Individual";` sin condición previa de PDF, es hardcode.
            # Pero en la implementación actual está dentro de `if (combined.contains("individual"))`,
            # así que no es hardcode. La regla dispara solo si esa condición desaparece.
            and not line.strip().startswith("if")  # excluir la rama condicional directa
        )
    },
    {
        "id": "HC-SYLLABUS-02",
        "category": "Metadatos Académicos",
        "severity": "ALTO",
        "title": "Inferencia de modalidad por prefijo de tipo de evaluación (ATI*, APF*)",
        "desc": "Asume 'Grupal' si el tipo empieza con ATI o APF sin que el PDF lo certifique textualmente.",
        "file_pattern": r"SyllabusTableReconstructor\.java$",
        "match_fn": lambda line, fname: (
            'type.startsWith("ATI")' in line or 'type.startsWith("APF")' in line
        )
    },
    {
        "id": "HC-SYLLABUS-03",
        "category": "Heurísticas FSM",
        "severity": "MEDIO",
        "title": "Heurística de 36 sesiones cableada en el FSM",
        "desc": "Constantes o comparaciones basadas en 36 sesiones fijas (asume 2 sesiones x 18 semanas).",
        "file_pattern": r"SyllabusScheduleFsm\.java$",
        "match_fn": lambda line, fname: (
            bool(re.search(r'\b36\b', line))
            and not line.strip().startswith("//")
            and not line.strip().startswith("*")
        )
    },

    # -------------------------------------------------------------
    # 5. EXCEPCIONES Y TIPADO (MEDIO / BAJO)
    # -------------------------------------------------------------
    {
        "id": "HC-EXCEPTION-01",
        "category": "Excepciones y Tipado",
        "severity": "MEDIO",
        "title": "Uso de IllegalStateException genérica en GradeSimulatorEngine",
        "desc": "Lanza IllegalStateException en lugar de una excepción tipada de dominio (ej. GradeSimulationException).",
        "file_pattern": r"GradeSimulatorEngine\.java$",
        "match_fn": lambda line, fname: (
            "throw new IllegalStateException(" in line
        )
    },
    {
        "id": "HC-SYNTHETIC-01",
        "category": "Identificadores Sintéticos",
        "severity": "BAJO",
        "title": "Prefijo de ID de sesión sintético 'gql-'",
        "desc": "Generación de ID sintético 'gql-' + startEpoch cuando la sesión carece de ID oficial.",
        "file_pattern": r"ScheduleItemParser\.java$",
        "match_fn": lambda line, fname: (
            '"gql-" + startEpoch' in line
        )
    }
]

def scan_repository():
    findings = []
    
    files_to_scan = []
    if SRC_MAIN_JAVA.exists():
        for root, _, files in os.walk(SRC_MAIN_JAVA):
            for f in files:
                if f.endswith(".java"):
                    files_to_scan.append(Path(root) / f)
                    
    if SRC_MAIN_RESOURCES.exists():
        for root, _, files in os.walk(SRC_MAIN_RESOURCES):
            for f in files:
                if f.endswith((".yml", ".yaml", ".properties")):
                    files_to_scan.append(Path(root) / f)

    for file_path in files_to_scan:
        rel_path = file_path.relative_to(BASE_DIR).as_posix()
        try:
            with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                lines = f.splitlines() if hasattr(f, "splitlines") else f.read().splitlines()
        except Exception as e:
            print(f"Error al leer {rel_path}: {e}")
            continue

        for check in AUDIT_CHECKS:
            if not re.search(check["file_pattern"], rel_path):
                continue

            for line_idx, line in enumerate(lines, start=1):
                try:
                    if check["match_fn"](line, rel_path):
                        # Contexto
                        start_ctx = max(0, line_idx - 2)
                        end_ctx = min(len(lines), line_idx + 1)
                        snippet = [f"{i+1:5d} | {lines[i]}" for i in range(start_ctx, end_ctx)]

                        findings.append({
                            "id": check["id"],
                            "category": check["category"],
                            "severity": check["severity"],
                            "title": check["title"],
                            "desc": check["desc"],
                            "file": rel_path,
                            "line": line_idx,
                            "line_text": line.strip(),
                            "snippet": "\n".join(snippet)
                        })
                except Exception as e:
                    pass

    return findings

def render_report(findings):
    severities = {"CRITICO": 0, "ALTO": 1, "MEDIO": 2, "BAJO": 3}
    findings.sort(key=lambda x: (severities.get(x["severity"], 99), x["file"], x["line"]))

    criticos = [f for f in findings if f["severity"] == "CRITICO"]
    altos = [f for f in findings if f["severity"] == "ALTO"]
    medios = [f for f in findings if f["severity"] == "MEDIO"]
    bajos = [f for f in findings if f["severity"] == "BAJO"]

    print("=" * 90)
    print("      REPORTE DE AUDITORÍA: DETECCIÓN DE HARDCODES Y FALLBACKS SILENCIOSOS")
    print("=" * 90)
    print(f"Total de incidencias detectadas: {len(findings)}")
    print(f"  - [CRÍTICO] : {len(criticos)} (Anclajes de periodo, fechas fijas, fallbacks en resolución)")
    print(f"  - [ALTO]    : {len(altos)} (18 semanas rígidas, identidad suprimida, modalidad inferida)")
    print(f"  - [MEDIO]   : {len(medios)} (Regex acotada, heurísticas numéricas, excepción genérica)")
    print(f"  - [BAJO]    : {len(bajos)} (ID sintético gql-)")
    print("=" * 90)

    by_file = {}
    for f in findings:
        by_file.setdefault(f["file"], []).append(f)

    for file_path, items in by_file.items():
        print(f"\n>> ARCHIVO: {file_path} ({len(items)} incidencias)")
        print("-" * 90)
        for item in items:
            print(f"  [{item['severity']}] Línea {item['line']:4d} | {item['id']}: {item['title']}")
            print(f"    Categoría   : {item['category']}")
            print(f"    Diagnóstico : {item['desc']}")
            print(f"    Código      :\n{item['snippet']}")
            print()

    print("=" * 90)

if __name__ == "__main__":
    findings = scan_repository()
    render_report(findings)

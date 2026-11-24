"""Regenerate the complete Java fallback after editing the shipped .lang files."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
source = '''package pocketmod;

import java.util.HashMap;
import java.util.Map;

/** Generated fallback; includes every shipped translation, including Exile. */
public final class Lang {
    public static final Map<String,String> RU = new HashMap<>();
    public static final Map<String,String> EN = new HashMap<>();
    static {
'''
methods = []
for code, target in [('ru_ru', 'RU'), ('en_us', 'EN')]:
    entries = {}
    for line in (root / f'src/main/resources/assets/pocketmod/lang/{code}.lang').read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            entries[key] = value
    values = sorted(entries.items())
    for offset in range(0, len(values), 70):
        name = code + str(offset // 70)
        source += f'        {name}({target});\n'
        puts = ''.join('        m.put(' + json.dumps(k, ensure_ascii=False) + ', ' +
                       json.dumps(v, ensure_ascii=False) + ');\n' for k, v in values[offset:offset+70])
        methods.append('    private static void ' + name + '(Map<String,String> m) {\n' + puts + '    }\n')
source += '    }\n' + ''.join(methods) + '}\n'
(root / 'src/main/java/pocketmod/Lang.java').write_text(source)

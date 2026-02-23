import re, os

base = 'app/src/main/java/com/lolita/app/ui/screen'
files = [
    f'{base}/item/ItemDetailScreen.kt',
    f'{base}/item/ItemListScreen.kt',
    f'{base}/item/ItemEditScreen.kt',
    f'{base}/item/FilteredItemListScreen.kt',
    f'{base}/item/WishlistScreen.kt',
    f'{base}/item/RecommendationScreen.kt',
    f'{base}/outfit/OutfitLogListScreen.kt',
    f'{base}/outfit/OutfitLogDetailScreen.kt',
    f'{base}/outfit/OutfitLogEditScreen.kt',
    f'{base}/outfit/QuickOutfitLogScreen.kt',
    f'{base}/coordinate/CoordinateListScreen.kt',
    f'{base}/coordinate/CoordinateDetailScreen.kt',
    f'{base}/coordinate/CoordinateEditScreen.kt',
    f'{base}/price/PriceManageScreen.kt',
    f'{base}/price/PriceEditScreen.kt',
    f'{base}/price/PaymentManageScreen.kt',
    f'{base}/price/PaymentEditScreen.kt',
    f'{base}/settings/BrandManageScreen.kt',
    f'{base}/settings/CategoryManageScreen.kt',
    f'{base}/settings/StyleManageScreen.kt',
    f'{base}/settings/SeasonManageScreen.kt',
    f'{base}/settings/BackupRestoreScreen.kt',
    f'{base}/settings/ThemeSelectScreen.kt',
    f'{base}/import/TaobaoImportScreen.kt',
    f'{base}/import/ImportDetailScreen.kt',
    f'{base}/calendar/PaymentCalendarScreen.kt',
    f'{base}/common/SortOption.kt',
]

# Icon key mapping (longest first for matching)
icon_map = {
    'Icons.AutoMirrored.Filled.ArrowBack': 'IconKey.ArrowBack',
    'Icons.AutoMirrored.Filled.ArrowForward': 'IconKey.ArrowForward',
    'Icons.AutoMirrored.Filled.KeyboardArrowLeft': 'IconKey.KeyboardArrowLeft',
    'Icons.AutoMirrored.Filled.KeyboardArrowRight': 'IconKey.KeyboardArrowRight',
    'Icons.Default.AddCircleOutline': 'IconKey.Add',
    'Icons.Default.AddPhotoAlternate': 'IconKey.AddPhoto',
    'Icons.Default.AutoAwesome': 'IconKey.Star',
    'Icons.Default.CheckCircle': 'IconKey.CheckCircle',
    'Icons.Default.DateRange': 'IconKey.CalendarMonth',
    'Icons.Default.FileOpen': 'IconKey.FileOpen',
    'Icons.Default.LinkOff': 'IconKey.LinkOff',
    'Icons.Default.Add': 'IconKey.Add',
    'Icons.Default.Delete': 'IconKey.Delete',
    'Icons.Default.Edit': 'IconKey.Edit',
    'Icons.Default.Search': 'IconKey.Search',
    'Icons.Default.Check': 'IconKey.Save',
    'Icons.Default.Close': 'IconKey.Close',
    'Icons.Default.Star': 'IconKey.Star',
    'Icons.Default.Link': 'IconKey.Link',
}

skin_import1 = 'import com.lolita.app.ui.theme.skin.icon.IconKey'
skin_import2 = 'import com.lolita.app.ui.theme.skin.icon.SkinIcon'
def find_matching_paren(text, start):
    """Find the index of the closing paren matching the open paren at start."""
    count = 0
    for i in range(start, len(text)):
        if text[i] == '(':
            count += 1
        elif text[i] == ')':
            count -= 1
            if count == 0:
                return i
    return -1

def parse_icon_call(call_text):
    """Parse Icon(...) call and return (icon_ref, modifier, tint) or None."""
    # call_text is like: Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
    # Strip Icon( and trailing )
    inner = call_text[5:-1].strip()

    # Find which icon reference is used
    matched_icon = None
    matched_key = None
    for icon_ref, key in sorted(icon_map.items(), key=lambda x: -len(x[0])):
        if inner.startswith(icon_ref):
            matched_icon = icon_ref
            matched_key = key
            break

    if not matched_icon:
        return None

    rest = inner[len(matched_icon):].strip()
    if rest.startswith(','):
        rest = rest[1:].strip()

    # Now parse remaining args, respecting nested parens
    # Split by top-level commas
    args = []
    current = []
    depth = 0
    for ch in rest:
        if ch == '(' or ch == '{':
            depth += 1
            current.append(ch)
        elif ch == ')' or ch == '}':
            depth -= 1
            current.append(ch)
        elif ch == ',' and depth == 0:
            args.append(''.join(current).strip())
            current = []
        else:
            current.append(ch)
    if current:
        last = ''.join(current).strip()
        if last:
            args.append(last)

    modifier_val = None
    tint_val = None

    for arg in args:
        if arg.startswith('modifier') and '=' in arg:
            modifier_val = arg.split('=', 1)[1].strip()
        elif arg.startswith('tint') and '=' in arg:
            tint_val = arg.split('=', 1)[1].strip()
        # positional args: contentDescription (string/null) - skip
        # Also handle: Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = ...)
        # positional: 2nd=contentDescription, 3rd=modifier, 4th could be tint
        # We need to detect positional modifier

    # Handle positional args (non-named)
    positional = [a for a in args if '=' not in a or a.startswith('Modifier') or a.startswith('modifier')]
    # Re-parse: positional args after icon ref are: contentDescription, modifier
    pos_idx = 0
    for arg in args:
        stripped = arg.strip()
        if '=' in stripped and not stripped.startswith('Modifier'):
            # named arg
            name = stripped.split('=', 1)[0].strip()
            val = stripped.split('=', 1)[1].strip()
            if name == 'modifier':
                modifier_val = val
            elif name == 'tint':
                tint_val = val
            elif name == 'contentDescription':
                pass  # skip
        else:
            # positional
            if pos_idx == 0:
                # contentDescription - skip
                pass
            elif pos_idx == 1:
                # modifier (positional)
                if modifier_val is None:
                    modifier_val = stripped
            elif pos_idx == 2:
                # tint (positional)
                if tint_val is None:
                    tint_val = stripped
            pos_idx += 1

    return (matched_key, modifier_val, tint_val)

def build_skin_icon_call(key, modifier_val, tint_val):
    parts = [key]
    if modifier_val:
        parts.append(f'modifier = {modifier_val}')
    if tint_val:
        parts.append(f'tint = {tint_val}')
    if len(parts) == 1:
        return f'SkinIcon({key})'
    else:
        return f'SkinIcon({", ".join(parts)})'

def process_file(filepath):
    if not os.path.exists(filepath):
        print(f'MISSING: {filepath}')
        return 0

    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    replacements = 0

    # Find and replace all Icon(Icons. calls
    # We search for the pattern and extract the full balanced call
    new_content = []
    i = 0
    while i < len(content):
        # Look for Icon(Icons.
        idx = content.find('Icon(Icons.', i)
        if idx == -1:
            new_content.append(content[i:])
            break

        # Add everything before this match
        new_content.append(content[i:idx])

        # Find the matching closing paren
        open_paren = idx + 4  # position of '(' in Icon(
        close_paren = find_matching_paren(content, open_paren)
        if close_paren == -1:
            # Can't find matching paren, skip
            new_content.append(content[idx:idx+11])
            i = idx + 11
            continue

        call_text = content[idx:close_paren+1]
        result = parse_icon_call(call_text)

        if result:
            key, mod, tint = result
            new_call = build_skin_icon_call(key, mod, tint)
            new_content.append(new_call)
            replacements += 1
        else:
            # No matching icon, keep original
            new_content.append(call_text)

        i = close_paren + 1

    content = ''.join(new_content)

    # Add imports if we made replacements
    if replacements > 0:
        if skin_import1 not in content:
            lines = content.split('\n')
            last_import_idx = -1
            for li, line in enumerate(lines):
                if line.startswith('import '):
                    last_import_idx = li
            if last_import_idx >= 0:
                lines.insert(last_import_idx + 1, skin_import2)
                lines.insert(last_import_idx + 1, skin_import1)
            content = '\n'.join(lines)

        if skin_import2 not in content:
            lines = content.split('\n')
            last_import_idx = -1
            for li, line in enumerate(lines):
                if line.startswith('import '):
                    last_import_idx = li
            if last_import_idx >= 0:
                lines.insert(last_import_idx + 1, skin_import2)
            content = '\n'.join(lines)

    # Remove material.icons imports
    lines = content.split('\n')
    new_lines = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('import androidx.compose.material.icons'):
            continue
        new_lines.append(line)
    content = '\n'.join(new_lines)

    # Remove explicit Icon import if no remaining Icon( calls
    # Check for Icon( that is NOT SkinIcon(
    has_remaining = False
    for m in re.finditer(r'\bIcon\(', content):
        pos = m.start()
        if pos >= 4 and content[pos-4:pos] == 'Skin':
            continue
        has_remaining = True
        break

    if not has_remaining:
        lines = content.split('\n')
        new_lines = []
        for line in lines:
            if line.strip() == 'import androidx.compose.material3.Icon':
                continue
            new_lines.append(line)
        content = '\n'.join(new_lines)

    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'OK: {filepath} ({replacements} replacements)')
    else:
        print(f'NO CHANGE: {filepath}')

    return replacements

total = 0
for fp in files:
    total += process_file(fp)
print(f'\nTotal replacements: {total}')

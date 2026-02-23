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
def find_matching_paren(text, start):
    count = 0
    for i in range(start, len(text)):
        if text[i] == '(':
            count += 1
        elif text[i] == ')':
            count -= 1
            if count == 0:
                return i
    return -1

def parse_icon_args(inner, matched_icon):
    """Parse the args after the icon reference in an Icon() call."""
    rest = inner.replace(matched_icon, '', 1).strip()
    if rest.startswith(','):
        rest = rest[1:].strip()

    # Split by top-level commas
    args = []
    current = []
    depth = 0
    for ch in rest:
        if ch in '({':
            depth += 1
            current.append(ch)
        elif ch in ')}':
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
    pos_idx = 0

    for arg in args:
        stripped = arg.strip()
        if '=' in stripped and not stripped.startswith('Modifier'):
            name = stripped.split('=', 1)[0].strip()
            val = stripped.split('=', 1)[1].strip()
            if name == 'modifier':
                modifier_val = val
            elif name == 'tint':
                tint_val = val
        else:
            if pos_idx == 0:
                pass  # contentDescription
            elif pos_idx == 1:
                if modifier_val is None:
                    modifier_val = stripped
            elif pos_idx == 2:
                if tint_val is None:
                    tint_val = stripped
            pos_idx += 1

    return modifier_val, tint_val

def build_skin_icon(key, mod, tint, indent):
    parts = [key]
    if mod:
        parts.append(f'modifier = {mod}')
    if tint:
        parts.append(f'tint = {tint}')
    return f'{indent}SkinIcon({", ".join(parts)})'


def process_file(filepath):
    if not os.path.exists(filepath):
        print(f'MISSING: {filepath}')
        return 0

    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    replacements = 0

    # Find multi-line Icon( calls where Icons. is on the next line
    # Pattern: Icon(\n...Icons.xxx...)
    # We search for "Icon(" followed by whitespace/newline then "Icons."
    new_content = []
    i = 0
    while i < len(content):
        # Look for Icon( that is NOT SkinIcon(
        idx = content.find('Icon(', i)
        if idx == -1:
            new_content.append(content[i:])
            break

        # Check it's not SkinIcon
        if idx >= 4 and content[idx-4:idx] == 'Skin':
            new_content.append(content[i:idx+5])
            i = idx + 5
            continue

        # Check if this Icon( contains an Icons. reference (possibly on next line)
        open_paren = idx + 4
        close_paren = find_matching_paren(content, open_paren)
        if close_paren == -1:
            new_content.append(content[i:idx+5])
            i = idx + 5
            continue

        call_text = content[idx:close_paren+1]
        inner = call_text[5:-1]  # strip Icon( and )

        # Find which icon is used
        matched_icon = None
        matched_key = None
        for icon_ref, key in sorted(icon_map.items(), key=lambda x: -len(x[0])):
            if icon_ref in inner:
                matched_icon = icon_ref
                matched_key = key
                break

        if matched_icon:
            # Get indentation of the Icon( line
            line_start = content.rfind('\n', 0, idx) + 1
            indent = ''
            for ch in content[line_start:idx]:
                if ch in ' \t':
                    indent += ch
                else:
                    break

            mod, tint = parse_icon_args(inner, matched_icon)
            new_call = build_skin_icon(matched_key, mod, tint, indent)

            new_content.append(content[i:idx])
            new_content.append(new_call.lstrip())  # lstrip because indent already in content[i:idx]
            i = close_paren + 1
            replacements += 1
        else:
            new_content.append(content[i:close_paren+1])
            i = close_paren + 1

    content = ''.join(new_content)

    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f'OK: {filepath} ({replacements} multi-line replacements)')
    else:
        print(f'NO CHANGE: {filepath}')

    return replacements

total = 0
for fp in files:
    total += process_file(fp)
print(f'\nTotal multi-line replacements: {total}')

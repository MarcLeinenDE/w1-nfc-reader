# Translations

W1 NFC Reader keeps user-facing text in Android string resources.

## Supported languages

The first public release includes:

- English (fallback)
- German (`de`)
- French (`fr`)
- Polish (`pl`)
- Dutch (`nl`)
- Lithuanian (`lt`)

The app also enables Android RTL layout support so future right-to-left translations do not require a separate layout architecture.

## Translation changes

When adding or changing a user-facing string:

1. update the English fallback resource;
2. update all currently supported translations in the same change;
3. preserve formatting placeholders such as `%1$s`, `%2$d` and escaped characters;
4. run the translation consistency check;
5. verify that long labels still fit on a representative small display.

Run:

```bash
python3 scripts/check_product_i18n.py
```

## New languages

New language contributions are welcome. Prefer complete translations of the product-facing resource sets rather than a partial locale that falls back unpredictably for major screens.

Do not translate product names, protocol identifiers, units or trademarked names when doing so would make technical meaning ambiguous.

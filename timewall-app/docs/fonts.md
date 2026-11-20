# Font assets

The selector is generated from `DashboardFont`; preference values are filenames,
not ordinal positions. System Roboto remains the default. Removed fonts fall back
to the system font; Exo 2 and Gontserrat keep their existing preference filenames.

New families: Firjar, Montserrat, Noto Sans, Open Sans, Raleway, Roboto Condensed,
Roboto Slab and Shantell Sans. The supplied downloads include OFL 1.1 notices,
except Roboto Slab, which includes Apache License 2.0. Each license is retained
beside its static runtime font in assets. Original downloads, README files and
licenses are preserved under `design/fonts`, outside the APK.

## Android 4.4 compatibility and reproduction

Install `fonttools==4.60.2` in a Python environment and run
`python design/fonts/prepare_fonts.py` from this project. Every variable axis is
pinned: weight 400, remaining axes at their defaults. Fonts contain static outlines
and no `fvar` table, as checked by the unit suite. Runtime files are at the assets
root and stored uncompressed by Android's build.

Raleway declares a Reserved Font Name; its modified static font has the internal
family name `TW Rale`. The selector describes its source family as Raleway.
Copyright/license metadata is retained. Static conversion is a modification of
the source font; it does not imply endorsement by its authors. Roboto Slab's
modification consists of static instancing and Regular name normalization.

FontAwesome is an independent OFL icon font required by the UI, not an option in
the text font selector. Its existing font and license are restored unchanged.
Exo 2 and Gontserrat are retained unchanged with their embedded license metadata.

The new font families have invariant proper names in every interface language;
the system-font entry continues using the existing translated resource.
Static structure and packaging tests do not replace rendering checks on KitKat.

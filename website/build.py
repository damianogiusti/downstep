#!/usr/bin/env python3
"""Builds the static site: one HTML file per page and language, into public/.

Templates in templates/ hold the markup; texts live in i18n/<lang>.toml. A template uses:

    {{ home.h1 }}        a text from the language file (it may contain HTML and other tags)
    {{ page.title }}     the same, for the page being built (here: home.title)
    {{> header }}        templates/_header.html
    {{ @icon:tune }}     inline SVG from templates/icons/ (the app's own Material icons)
    {{ @file }}          download URL of the page's target (FILES below)
    {{ @root }}          relative path to the site root ("", "../", ...)
    {{ @lang }}          language code
    {{ @url:why }}       relative link to another page, in the same language
    {{ @alt }}           relative link to this page in the other language
    {{ @altlang }}       the other language code
    {{ @base }}          absolute site URL (BASE_URL), for links that must not be relative (og:image)
    {{ @jsonld }}        schema.org data for search engines and AI assistants (see jsonld())

A missing text or page stops the build, so the two languages cannot drift apart silently.
Python standard library only: python3 build.py, then serve public/.
"""

import json
import re
import shutil
import sys
import tomllib
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "public"
LANGS = ["en", "it"]

# Page id -> path per language, and the template when it is not <id>.html.
# English stays at the root, Italian under /it/ with Italian slugs.
PAGES = {
    "home": {"en": "", "it": "it/"},
    "mac": {"en": "download/mac/", "it": "it/scarica/mac/", "template": "download"},
    "windows": {"en": "download/windows/", "it": "it/scarica/windows/", "template": "download"},
    "why": {"en": "why-free/", "it": "it/perche-gratis/"},
    "more": {"en": "why-free/more/", "it": "it/perche-gratis/dettagli/"},
}

RELEASES = "https://github.com/damianogiusti/downstep/releases/latest/download/"
FILES = {"mac": RELEASES + "Downstep-mac.dmg", "windows": RELEASES + "Downstep-windows-x64.zip"}

# Canonical and hreflang links are absolute, and sitemap.xml is written. With the trailing slash:
# page paths have none at the start.
BASE_URL = "https://downstep.app/"

AUTHOR = {"@type": "Person", "name": "Damiano Giusti", "url": "https://www.damianogiusti.com"}

TAG = re.compile(r"\{\{\s*(>?)\s*([@\w.:-]+)\s*\}\}")


def flatten(table, prefix=""):
    out = {}
    for key, value in table.items():
        name = f"{prefix}{key}"
        if isinstance(value, dict):
            out.update(flatten(value, name + "."))
        else:
            out[name] = value.strip() if isinstance(value, str) else value
    return out


def relative(from_dir, to_dir):
    depth = from_dir.count("/")
    return "../" * depth + to_dir or "./"


def jsonld(page, lang, texts):
    """The app as a SoftwareApplication on the home page. Texts come from i18n."""
    url = BASE_URL + PAGES[page][lang]
    if page == "home":
        data = [
            {"@context": "https://schema.org", "@type": "WebSite", "name": "Downstep", "url": url, "inLanguage": lang},
            {
                "@context": "https://schema.org",
                "@type": "SoftwareApplication",
                "name": "Downstep",
                "url": url,
                "description": texts["schema.app_description"],
                "applicationCategory": "MultimediaApplication",
                "applicationSubCategory": texts["schema.category"],
                "operatingSystem": "macOS 11+, Windows 10, Windows 11",
                "offers": {"@type": "Offer", "price": "0", "priceCurrency": "EUR"},
                "isAccessibleForFree": True,
                "downloadUrl": list(FILES.values()),
                "image": BASE_URL + "assets/icon.png",
                "screenshot": BASE_URL + "assets/screens/practice-light.webp",
                "featureList": texts["schema.features"],
                "inLanguage": LANGS,
                "license": "https://www.gnu.org/licenses/gpl-3.0.html",
                "author": AUTHOR,
                "sameAs": ["https://github.com/damianogiusti/downstep"],
            },
        ]
    else:
        return ""
    # "</" would close the script element early.
    body = json.dumps(data, ensure_ascii=False, indent=1).replace("</", "<\\/")
    return f'<script type="application/ld+json">\n{body}\n</script>'


def render(text, lang, page, texts, other):
    here = PAGES[page][lang]

    def replace(match):
        include, name = match.groups()
        if include:
            return (HERE / "templates" / f"_{name}.html").read_text()
        if name.startswith("@icon:"):
            return (HERE / "templates" / "icons" / f"{name[6:]}.svg").read_text()
        if name == "@file":
            return FILES[page]
        if name == "@root":
            return "../" * here.count("/")
        if name == "@lang":
            return lang
        if name == "@altlang":
            return other
        if name == "@alt":
            return relative(here, PAGES[page][other])
        if name.startswith("@url:"):
            target = name[5:]
            if target not in PAGES:
                sys.exit(f"{page} ({lang}): unknown page '{target}'")
            return relative(here, PAGES[target][lang])
        if name == "@canonical":
            return BASE_URL + here if BASE_URL else "./"
        if name == "@base":
            return BASE_URL or "../" * here.count("/")
        if name == "@jsonld":
            return jsonld(page, lang, texts) if BASE_URL else ""
        if name == "@hreflang":
            # x-default: visitors in other languages get English.
            alternates = [(code, code) for code in LANGS] + [("x-default", "en")]
            return "\n".join(
                f'<link rel="alternate" hreflang="{code}" href="{BASE_URL + PAGES[page][target] if BASE_URL else relative(here, PAGES[page][target])}">'
                for code, target in alternates
            )
        if name.startswith("page."):
            name = page + name[4:]
        if name not in texts:
            sys.exit(f"{page} ({lang}): missing text '{name}' in i18n/{lang}.toml")
        return str(texts[name])

    # Texts can contain tags too (links to other pages), so render until nothing changes.
    for _ in range(5):
        rendered = TAG.sub(replace, text)
        if rendered == text:
            return rendered
        text = rendered
    sys.exit(f"{page} ({lang}): tags nested too deep")


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    shutil.copytree(HERE / "static", OUT)
    texts = {lang: flatten(tomllib.loads((HERE / "i18n" / f"{lang}.toml").read_text())) for lang in LANGS}
    missing = set(texts["en"]) ^ set(texts["it"])
    if missing:
        sys.exit("keys in only one language: " + ", ".join(sorted(missing)))
    for page, paths in PAGES.items():
        template = (HERE / "templates" / f"{paths.get('template', page)}.html").read_text()
        for lang in LANGS:
            other = next(code for code in LANGS if code != lang)
            html = render(template, lang, page, texts[lang], other)
            target = OUT / PAGES[page][lang] / "index.html"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(html)
            print("wrote", target.relative_to(HERE))
    if BASE_URL:
        urls = "".join(f"  <url><loc>{BASE_URL}{paths[lang]}</loc></url>\n" for paths in PAGES.values() for lang in LANGS)
        (OUT / "sitemap.xml").write_text(
            f'<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n{urls}</urlset>\n'
        )
        # Every crawler is welcome, search engines and AI assistants alike: being found is the point.
        (OUT / "robots.txt").write_text(f"User-agent: *\nAllow: /\n\nSitemap: {BASE_URL}sitemap.xml\n")


if __name__ == "__main__":
    main()

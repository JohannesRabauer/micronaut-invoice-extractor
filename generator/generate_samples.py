"""Generates fake invoices (PDF) and receipts (PNG) plus the ground truth for them.

All vendors, numbers and IBANs are made up. Because we generate the documents, the correct
values are known, so the extraction can be scored exactly (see scripts/evaluate.py).

    pip install reportlab pillow
    python generator/generate_samples.py
"""
import json
import os
import random
from datetime import date
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "samples"
random.seed(42)

D = Decimal


def q(x):
    return D(x).quantize(D("0.01"), rounding=ROUND_HALF_UP)


# ---------------------------------------------------------------- formatting

MONTHS = {
    "de": ["Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September",
           "Oktober", "November", "Dezember"],
    "fr": ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre",
           "octobre", "novembre", "décembre"],
    "en": ["January", "February", "March", "April", "May", "June", "July", "August", "September",
           "October", "November", "December"],
}


def fmt_date(d, loc):
    if loc == "de":
        return d.strftime("%d.%m.%Y")
    if loc == "us":
        return d.strftime("%m/%d/%Y")
    if loc == "fr":
        return f"{d.day} {MONTHS['fr'][d.month - 1]} {d.year}"
    return f"{d.day} {MONTHS['en'][d.month - 1]} {d.year}"


def fmt_money(x, loc, cur):
    x = q(x)
    sign = "-" if x < 0 else ""
    s = f"{abs(x):,.2f}"
    if loc == "de":
        s = s.replace(",", "X").replace(".", ",").replace("X", ".")
        return f"{sign}{s} €"
    if loc == "fr":
        s = s.replace(",", " ").replace(".", ",")
        return f"{sign}{s} €"
    sym = {"GBP": "£", "USD": "$"}[cur]
    return f"{sign}{sym}{s}"


# ---------------------------------------------------------------- invoices

INVOICES = [
    dict(id="invoice-01-nordlicht", loc="de", cur="EUR", vendor="Nordlicht Software GmbH",
         addr=["Hafenstraße 14", "20457 Hamburg", "Deutschland"], customer=["Kleinschmidt Logistik KG", "Am Güterbahnhof 3", "90402 Nürnberg"],
         number="RE-2026-1187", date=date(2026, 10, 28), labels=("Rechnung", "Rechnungsnummer", "Datum", "Pos.", "Beschreibung", "Menge", "Einzelpreis", "Gesamt", "Zwischensumme", "MwSt", "Gesamtbetrag"),
         items=[("Beratung Architektur-Review (Stunden)", D(14), D("115.00"), D("0.19")),
                ("Workshop Vorbereitung", D(1), D("480.00"), D("0.19")),
                ("Reisekosten Pauschale", D(1), D("95.50"), D("0.19"))], layout="left", grid=True),
    dict(id="invoice-02-brightwave", loc="en", cur="GBP", vendor="Brightwave Cloud Ltd",
         addr=["22 Canal Street", "Manchester M1 3EZ", "United Kingdom"], customer=["Ashdown & Pike Architects", "9 Gresham Row", "Bristol BS1 4QA"],
         number="BW-004519", date=date(2026, 11, 3), labels=("Invoice", "Invoice no.", "Date", "#", "Description", "Qty", "Unit price", "Amount", "Subtotal", "VAT 20%", "Total due"),
         items=[("Managed Kubernetes cluster, Oct 2026", D(1), D("640.00"), D("0.20")),
                ("Object storage 2.4 TB", D(24), D("3.90"), D("0.20")),
                ("Premium support plan", D(1), D("199.00"), D("0.20")),
                ("Static IP address", D(3), D("4.25"), D("0.20"))], layout="right", grid=False),
    dict(id="invoice-03-summit", loc="us", cur="USD", vendor="Summit Office Supply Inc.",
         addr=["1180 Mill Road, Suite 4", "Columbus, OH 43215", "USA"], customer=["Harlan Dental Group", "77 Fairview Ave", "Dayton, OH 45402"],
         number="SO-88213", date=date(2026, 10, 19), labels=("INVOICE", "Invoice #", "Date", "Item", "Description", "Qty", "Price", "Amount", "Subtotal", "Sales tax (8.25%)", "Total"),
         items=[("Copy paper A4, 5000 sheets", D(6), D("38.90"), D("0.0825")),
                ("Ergonomic chair Model K2", D(2), D("249.00"), D("0.0825")),
                ("Whiteboard markers, 12 pack", D(4), D("11.49"), D("0.0825")),
                ("Shipping and handling", D(1), D("24.00"), D("0"))], layout="left", grid=True),
    dict(id="invoice-04-lumiere", loc="fr", cur="EUR", vendor="Atelier Lumière SARL",
         addr=["8 rue des Tanneurs", "69002 Lyon", "France"], customer=["Maison Girard & Fils", "41 avenue Foch", "75116 Paris"],
         number="F-2026-0342", date=date(2026, 11, 5), labels=("Facture", "Numéro de facture", "Date", "Réf.", "Désignation", "Qté", "Prix unitaire", "Montant HT", "Total HT", "TVA 20 %", "Total TTC"),
         items=[("Shooting photo produit (demi-journée)", D(2), D("390.00"), D("0.20")),
                ("Retouche numérique, 30 images", D(30), D("12.00"), D("0.20")),
                ("Licence d'utilisation 12 mois", D(1), D("450.00"), D("0.20"))], layout="right", grid=True),
    dict(id="invoice-05-bergmann", loc="de", cur="EUR", vendor="Bergmann Haustechnik",
         addr=["Lindenallee 7", "93047 Regensburg"], customer=["Hausverwaltung Pfeiffer", "Dachauplatz 12", "93047 Regensburg"],
         number="2026-0713", date=date(2026, 10, 30), labels=("Rechnung", "Rechnungsnr.", "Rechnungsdatum", "Pos.", "Leistung", "Anzahl", "Einzelpreis", "Betrag", "Netto", "MwSt", "Rechnungsbetrag"),
         items=[("Heizungswartung Mehrfamilienhaus", D(1), D("385.00"), D("0.19")),
                ("Ersatzteil Umwälzpumpe", D(2), D("142.60"), D("0.19")),
                ("Fachbuch Heizungsrecht (7 % MwSt)", D(1), D("34.90"), D("0.07")),
                ("Anfahrt", D(3), D("22.00"), D("0.19"))], layout="left", grid=False, two_rates=True),
    dict(id="invoice-06-pixelhafen", loc="de", cur="EUR", vendor="Pixelhafen Studio",
         addr=["Speicherstadt 3", "20457 Hamburg"], customer=["Jonas Reinhardt", "Birkenweg 5", "93152 Nittendorf"],
         number="PH-0098", date=date(2026, 11, 9), labels=("Rechnung", "Rechnungsnummer", "Datum", "Pos.", "Beschreibung", "Menge", "Preis", "Summe", "Zwischensumme", "MwSt", "Gesamt"),
         items=[("Logo-Design, 3 Entwürfe", D(1), D("750.00"), D("0.19")),
                ("Schriftlizenz Desktop", D(1), D("89.00"), D("0.19")),
                ("Treuerabatt", D(1), D("-75.00"), D("0.19")),
                ("Versand Druckdaten (USB)", D(1), D("6.90"), D("0.19"))], layout="right", grid=True),
]


def compute(doc):
    lines = []
    vat_by_rate = {}
    subtotal = D(0)
    for desc, qty, unit, rate in doc["items"]:
        net = q(qty * unit)
        lines.append((desc, qty, unit, net, rate))
        subtotal += net
        vat_by_rate[rate] = vat_by_rate.get(rate, D(0)) + net
    vat = sum((q(net * rate) for rate, net in vat_by_rate.items()), D(0))
    return lines, q(subtotal), q(vat), q(subtotal + vat)


def draw_invoice(doc, path):
    lab = doc["labels"]
    loc, cur = doc["loc"], doc["cur"]
    lines, subtotal, vat, total = compute(doc)
    c = canvas.Canvas(str(path), pagesize=A4)
    w, h = A4
    accent = random.choice([colors.HexColor("#1f4e79"), colors.HexColor("#7a2e2e"), colors.HexColor("#2f5d3a"), colors.HexColor("#333333")])
    right = doc["layout"] == "right"
    if right:
        c.setFillColor(accent)
        c.rect(0, h - 38 * mm, w, 38 * mm, fill=1, stroke=0)
        c.setFillColor(colors.white)
        c.setFont("Helvetica-Bold", 20)
        c.drawString(20 * mm, h - 20 * mm, doc["vendor"])
        c.setFont("Helvetica", 9)
        c.drawString(20 * mm, h - 27 * mm, " · ".join(doc["addr"]))
        c.setFillColor(colors.black)
        y = h - 58 * mm
        c.setFont("Helvetica-Bold", 16)
        c.drawRightString(w - 20 * mm, y, lab[0].upper())
    else:
        c.setFillColor(accent)
        c.setFont("Helvetica-Bold", 18)
        c.drawString(20 * mm, h - 25 * mm, doc["vendor"])
        c.setFillColor(colors.black)
        c.setFont("Helvetica", 9)
        yy = h - 31 * mm
        for line in doc["addr"]:
            c.drawString(20 * mm, yy, line)
            yy -= 4.5 * mm
        y = h - 58 * mm
        c.setFont("Helvetica-Bold", 16)
        c.drawString(20 * mm, y, lab[0])

    c.setFont("Helvetica", 9)
    yy = h - 75 * mm
    for line in doc["customer"]:
        c.drawString(20 * mm, yy, line)
        yy -= 4.5 * mm
    c.setFont("Helvetica", 10)
    c.drawRightString(w - 55 * mm, h - 75 * mm, f"{lab[1]}:")
    c.drawString(w - 52 * mm, h - 75 * mm, doc["number"])
    c.drawRightString(w - 55 * mm, h - 80.5 * mm, f"{lab[2]}:")
    c.drawString(w - 52 * mm, h - 80.5 * mm, fmt_date(doc["date"], loc))
    c.setFont("Helvetica", 8)
    c.setFillColor(colors.grey)
    c.drawString(w - 52 * mm, h - 86 * mm, "Kd.-Nr. " + str(random.randint(10000, 99999)))
    c.setFillColor(colors.black)

    # table
    top = h - 105 * mm
    x_pos, x_desc, x_qty, x_unit, x_amt = 20 * mm, 30 * mm, 118 * mm, 150 * mm, w - 20 * mm
    c.setFont("Helvetica-Bold", 9)
    c.drawString(x_pos, top, lab[3])
    c.drawString(x_desc, top, lab[4])
    c.drawRightString(x_qty, top, lab[5])
    c.drawRightString(x_unit, top, lab[6])
    c.drawRightString(x_amt, top, lab[7])
    c.line(20 * mm, top - 2 * mm, w - 20 * mm, top - 2 * mm)
    y = top - 8 * mm
    c.setFont("Helvetica", 9)
    for i, (desc, qty, unit, net, rate) in enumerate(lines, 1):
        c.drawString(x_pos, y, str(i))
        c.drawString(x_desc, y, desc)
        c.drawRightString(x_qty, y, f"{qty:g}")
        c.drawRightString(x_unit, y, fmt_money(unit, loc, cur))
        c.drawRightString(x_amt, y, fmt_money(net, loc, cur))
        if doc["grid"]:
            c.setStrokeColor(colors.lightgrey)
            c.line(20 * mm, y - 2.5 * mm, w - 20 * mm, y - 2.5 * mm)
            c.setStrokeColor(colors.black)
        y -= 7 * mm

    # totals
    y -= 4 * mm
    c.setFont("Helvetica", 10)
    c.drawRightString(x_unit, y, f"{lab[8]}:")
    c.drawRightString(x_amt, y, fmt_money(subtotal, loc, cur))
    y -= 6 * mm
    c.drawRightString(x_unit, y, f"{lab[9]}:")
    c.drawRightString(x_amt, y, fmt_money(vat, loc, cur))
    y -= 7 * mm
    c.setFont("Helvetica-Bold", 11)
    c.line(x_unit - 38 * mm, y + 5 * mm, w - 20 * mm, y + 5 * mm)
    c.drawRightString(x_unit, y, f"{lab[10]}:")
    c.drawRightString(x_amt, y, fmt_money(total, loc, cur))

    # noise at the bottom, like real invoices: bank details, register numbers, page marker
    c.setFont("Helvetica", 7.5)
    c.setFillColor(colors.grey)
    iban = "DE" + str(random.randint(10, 99)) + " " + " ".join(str(random.randint(1000, 9999)) for _ in range(4)) + " " + str(random.randint(10, 99))
    foot = [f"{doc['vendor']} · {doc['addr'][0]} · {doc['addr'][1]}",
            f"IBAN {iban} · BIC TESTDEFFXXX · USt-IdNr. DE{random.randint(100000000, 999999999)}",
            "Zahlbar innerhalb von 14 Tagen / payable within 14 days · Page 1 / 1"]
    yy = 22 * mm
    for line in foot:
        c.drawCentredString(w / 2, yy, line)
        yy -= 3.8 * mm
    c.showPage()
    c.save()

    return dict(file=f"invoices/{doc['id']}.pdf", vendor=doc["vendor"], invoiceNumber=doc["number"],
                date=doc["date"].isoformat(), currency=cur, items=len(lines),
                subtotal=float(subtotal), vat=float(vat), total=float(total))


# ---------------------------------------------------------------- receipts

RECEIPTS = [
    dict(id="receipt-01-supermarkt", loc="de", cur="EUR", vendor="FRISCHEMARKT MEIER", addr=["Regensburger Str. 41", "93051 Regensburg"],
         date=date(2026, 11, 14), time="17:42", gross=True,
         items=[("Vollmilch 3,5% 1L", 2, "1.19", "0.07"), ("Bio Eier 10er", 1, "3.49", "0.07"), ("Bananen 1.2kg", 1, "1.78", "0.07"),
                ("Waschmittel Color", 1, "7.95", "0.19"), ("Kaffeebohnen 500g", 1, "8.99", "0.07"), ("Mineralwasser 6x1,5L", 1, "3.54", "0.19")]),
    dict(id="receipt-02-cafe", loc="de", cur="EUR", vendor="Café Nordstern", addr=["Obere Bachgasse 9", "93047 Regensburg"],
         date=date(2026, 11, 12), time="09:15", gross=True,
         items=[("Cappuccino", 2, "3.80", "0.19"), ("Butterbrezel", 2, "2.20", "0.07"), ("Apfelstrudel", 1, "4.50", "0.07")]),
    dict(id="receipt-03-tankstelle", loc="de", cur="EUR", vendor="STERN TANKSTELLE", addr=["Industriering 2", "93055 Regensburg"],
         date=date(2026, 11, 10), time="06:58", gross=True,
         items=[("Super E10 38,42 L", 1, "67.36", "0.19"), ("Scheibenwischer-Wasser", 1, "4.99", "0.19"), ("Kaffee to go", 1, "2.60", "0.19")]),
    dict(id="receipt-04-diner", loc="us", cur="USD", vendor="Route 9 Diner", addr=["4420 Main St", "Springfield, IL 62704"],
         date=date(2026, 11, 8), time="12:31", gross=False, tax=D("0.0725"),
         items=[("Cheeseburger Deluxe", 2, "13.50", None), ("Fries", 1, "4.25", None), ("Chocolate Shake", 2, "6.00", None), ("Pie of the day", 1, "5.75", None)]),
    dict(id="receipt-05-pharmacy", loc="en", cur="GBP", vendor="Whitmore Pharmacy", addr=["15 High Street", "Oxford OX1 4AH"],
         date=date(2026, 11, 6), time="15:20", gross=True,
         items=[("Ibuprofen 400mg x24", 1, "3.29", "0.20"), ("Vitamin D 90 tabs", 1, "7.99", "0.20"), ("Hand cream 75ml", 2, "4.49", "0.20"), ("Plasters assorted", 1, "2.15", "0.20")]),
    dict(id="receipt-06-baumarkt", loc="de", cur="EUR", vendor="BAUMARKT HOFFMANN", addr=["Gewerbepark Süd 12", "93073 Neutraubling"],
         date=date(2026, 11, 2), time="11:07", gross=True,
         items=[("Akkuschrauber-Bit-Set", 1, "14.95", "0.19"), ("Dübel 8mm 100 St.", 2, "5.49", "0.19"), ("Wandfarbe weiß 10L", 1, "39.90", "0.19"), ("Malerkrepp 50m", 3, "2.79", "0.19"), ("Pflanzerde 40L", 1, "6.99", "0.07")]),
]


def find_font(size):
    for name in ("C:/Windows/Fonts/cour.ttf", "C:/Windows/Fonts/consola.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"):
        if os.path.exists(name):
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()


def receipt_lines(doc):
    loc, cur = doc["loc"], doc["cur"]
    rows, total_vat = [], D(0)
    gross_total = D(0)
    prepared = []
    for desc, qty, unit, rate in doc["items"]:
        amt = q(D(qty) * D(unit))
        prepared.append((desc, qty, D(unit), amt, D(rate) if rate else None))
    net_total = sum((p[3] for p in prepared), D(0))
    if doc["gross"]:
        by_rate = {}
        for _, _, _, amt, rate in prepared:
            by_rate[rate] = by_rate.get(rate, D(0)) + amt
        for rate, amt in by_rate.items():
            total_vat += q(amt - amt / (1 + rate))
        gross_total = net_total
        subtotal = None
    else:
        total_vat = q(net_total * doc["tax"])
        subtotal = net_total
        gross_total = net_total + total_vat
    return prepared, subtotal, total_vat, gross_total


def draw_receipt(doc, path):
    loc, cur = doc["loc"], doc["cur"]
    prepared, subtotal, vat, total = receipt_lines(doc)
    W = 46  # characters per line
    def row(left, right):
        return left[: W - len(right) - 1].ljust(W - len(right)) + right
    L = []
    for t in [doc["vendor"]] + doc["addr"]:
        L.append(t.center(W))
    L.append("")
    L.append(row(fmt_date(doc["date"], loc), doc["time"]))
    L.append("-" * W)
    for desc, qty, unit, amt, rate in prepared:
        if qty > 1:
            L.append(row(desc, fmt_money(amt, loc, cur)))
            L.append(f"   {qty} x {fmt_money(unit, loc, cur)}")
        else:
            L.append(row(desc, fmt_money(amt, loc, cur)))
    L.append("-" * W)
    if subtotal is not None:
        L.append(row("SUBTOTAL", fmt_money(subtotal, loc, cur)))
        L.append(row(f"TAX {doc['tax'] * 100:g}%", fmt_money(vat, loc, cur)))
        L.append(row("TOTAL", fmt_money(total, loc, cur)))
    else:
        label = {"de": "SUMME", "en": "TOTAL"}[loc]
        L.append(row(label, fmt_money(total, loc, cur)))
        rates = sorted({p[4] for p in prepared})
        L.append("")
        for rate in rates:
            amt = sum((p[3] for p in prepared if p[4] == rate), D(0))
            part = q(amt - amt / (1 + rate))
            name = {"de": "MwSt", "en": "VAT"}[loc]
            L.append(row(f"{name} {rate * 100:g}% inkl.", fmt_money(part, loc, cur)))
    L.append("")
    L.append(random.choice(["Vielen Dank für Ihren Einkauf!", "Thank you, see you soon!"]).center(W))
    L.append(f"TSE-Nr {random.randint(10**9, 10**10 - 1)}".center(W))
    L.append(f"Beleg {random.randint(1000, 9999)}  Kasse {random.randint(1, 4)}".center(W))

    font = find_font(22)
    lh = 28
    w_px, h_px = 700, lh * len(L) + 120
    paper = Image.new("RGB", (w_px, h_px), (246, 244, 238))
    d = ImageDraw.Draw(paper)
    for i, line in enumerate(L):
        d.text((24, 50 + i * lh), line, font=font, fill=(40, 40, 40))
    # fading thermal print: a faint horizontal band
    fade = Image.new("L", paper.size, 0)
    ImageDraw.Draw(fade).rectangle([0, int(h_px * 0.55), w_px, int(h_px * 0.62)], fill=60)
    paper = Image.composite(Image.new("RGB", paper.size, (246, 244, 238)), paper, fade.filter(ImageFilter.GaussianBlur(25)))
    # table background, rotation, noise, blur
    angle = random.uniform(-3.2, 3.2)
    rotated = paper.rotate(angle, expand=True, resample=Image.BICUBIC, fillcolor=(0, 0, 0))
    mask = Image.new("L", paper.size, 255).rotate(angle, expand=True)
    bg = Image.new("RGB", (rotated.width + 160, rotated.height + 120), (92, 74, 60))
    bg.paste(rotated, (80, 60), mask)
    noise = Image.effect_noise(bg.size, 18).convert("RGB")
    bg = Image.blend(bg, noise, 0.07).filter(ImageFilter.GaussianBlur(0.7))
    bg.save(path, quality=90)

    return dict(file=f"receipts/{doc['id']}.png", vendor=doc["vendor"], invoiceNumber=None,
                date=doc["date"].isoformat(), currency=cur, items=len(prepared),
                subtotal=float(subtotal) if subtotal is not None else None,
                vat=float(vat), total=float(total))


def main():
    (OUT / "invoices").mkdir(parents=True, exist_ok=True)
    (OUT / "receipts").mkdir(parents=True, exist_ok=True)
    truth = []
    for doc in INVOICES:
        truth.append(draw_invoice(doc, OUT / "invoices" / f"{doc['id']}.pdf"))
    for doc in RECEIPTS:
        truth.append(draw_receipt(doc, OUT / "receipts" / f"{doc['id']}.png"))
    (OUT / "ground-truth.json").write_text(json.dumps(truth, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"wrote {len(truth)} documents to {OUT}")


if __name__ == "__main__":
    main()

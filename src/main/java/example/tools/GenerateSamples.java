package example.tools;

import io.micronaut.serde.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Generates fake invoices (PDF) and receipts (PNG) plus the ground truth for them.
 * All vendors, numbers and IBANs are made up. Because we generate the documents, the correct
 * values are known, so the extraction can be scored exactly (see {@link Evaluate}).
 *
 * <pre>docker compose --profile tools run --rm generate</pre>
 */
public class GenerateSamples {

    private static final Random RND = new Random(42);

    private static BigDecimal q(BigDecimal x) {
        return x.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static float mm(double v) {
        return (float) (v * 72 / 25.4);
    }

    // ------------------------------------------------------------ formatting

    private static final String[] MONTHS_DE = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
            "September", "Oktober", "November", "Dezember"};
    private static final String[] MONTHS_FR = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août",
            "septembre", "octobre", "novembre", "décembre"};
    private static final String[] MONTHS_EN = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};

    static String fmtDate(LocalDate d, String loc) {
        return switch (loc) {
            case "de" -> "%02d.%02d.%d".formatted(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
            case "us" -> "%02d/%02d/%d".formatted(d.getMonthValue(), d.getDayOfMonth(), d.getYear());
            case "fr" -> d.getDayOfMonth() + " " + MONTHS_FR[d.getMonthValue() - 1] + " " + d.getYear();
            default -> d.getDayOfMonth() + " " + MONTHS_EN[d.getMonthValue() - 1] + " " + d.getYear();
        };
    }

    static String fmtMoney(BigDecimal x, String loc, String cur) {
        x = q(x);
        String sign = x.signum() < 0 ? "-" : "";
        String s = "%,.2f".formatted(x.abs()); // JVM default locale may differ, so fix the separators below
        s = s.replace(',', '#').replace('.', '~');
        switch (loc) {
            case "de":
                return sign + s.replace('#', '.').replace('~', ',') + " €";
            case "fr":
                return sign + s.replace('#', ' ').replace('~', ',') + " €";
            default:
                String sym = cur.equals("GBP") ? "£" : "$";
                return sign + sym + s.replace('#', ',').replace('~', '.');
        }
    }

    // ------------------------------------------------------------ invoices

    record Item(String desc, BigDecimal qty, BigDecimal unit, BigDecimal rate) {
    }

    record InvoiceDoc(String id, String loc, String cur, String vendor, List<String> addr, List<String> customer,
                      String number, LocalDate date, String[] labels, List<Item> items, boolean right, boolean grid) {
    }

    private static Item it(String desc, String qty, String unit, String rate) {
        return new Item(desc, d(qty), d(unit), d(rate));
    }

    private static List<InvoiceDoc> invoices() {
        return List.of(
                new InvoiceDoc("invoice-01-nordlicht", "de", "EUR", "Nordlicht Software GmbH",
                        List.of("Hafenstraße 14", "20457 Hamburg", "Deutschland"),
                        List.of("Kleinschmidt Logistik KG", "Am Güterbahnhof 3", "90402 Nürnberg"),
                        "RE-2026-1187", LocalDate.of(2026, 10, 28),
                        new String[]{"Rechnung", "Rechnungsnummer", "Datum", "Pos.", "Beschreibung", "Menge", "Einzelpreis", "Gesamt", "Zwischensumme", "MwSt", "Gesamtbetrag"},
                        List.of(it("Beratung Architektur-Review (Stunden)", "14", "115.00", "0.19"),
                                it("Workshop Vorbereitung", "1", "480.00", "0.19"),
                                it("Reisekosten Pauschale", "1", "95.50", "0.19")), false, true),
                new InvoiceDoc("invoice-02-brightwave", "en", "GBP", "Brightwave Cloud Ltd",
                        List.of("22 Canal Street", "Manchester M1 3EZ", "United Kingdom"),
                        List.of("Ashdown & Pike Architects", "9 Gresham Row", "Bristol BS1 4QA"),
                        "BW-004519", LocalDate.of(2026, 11, 3),
                        new String[]{"Invoice", "Invoice no.", "Date", "#", "Description", "Qty", "Unit price", "Amount", "Subtotal", "VAT 20%", "Total due"},
                        List.of(it("Managed Kubernetes cluster, Oct 2026", "1", "640.00", "0.20"),
                                it("Object storage 2.4 TB", "24", "3.90", "0.20"),
                                it("Premium support plan", "1", "199.00", "0.20"),
                                it("Static IP address", "3", "4.25", "0.20")), true, false),
                new InvoiceDoc("invoice-03-summit", "us", "USD", "Summit Office Supply Inc.",
                        List.of("1180 Mill Road, Suite 4", "Columbus, OH 43215", "USA"),
                        List.of("Harlan Dental Group", "77 Fairview Ave", "Dayton, OH 45402"),
                        "SO-88213", LocalDate.of(2026, 10, 19),
                        new String[]{"INVOICE", "Invoice #", "Date", "Item", "Description", "Qty", "Price", "Amount", "Subtotal", "Sales tax (8.25%)", "Total"},
                        List.of(it("Copy paper A4, 5000 sheets", "6", "38.90", "0.0825"),
                                it("Ergonomic chair Model K2", "2", "249.00", "0.0825"),
                                it("Whiteboard markers, 12 pack", "4", "11.49", "0.0825"),
                                it("Shipping and handling", "1", "24.00", "0")), false, true),
                new InvoiceDoc("invoice-04-lumiere", "fr", "EUR", "Atelier Lumière SARL",
                        List.of("8 rue des Tanneurs", "69002 Lyon", "France"),
                        List.of("Maison Girard & Fils", "41 avenue Foch", "75116 Paris"),
                        "F-2026-0342", LocalDate.of(2026, 11, 5),
                        new String[]{"Facture", "Numéro de facture", "Date", "Réf.", "Désignation", "Qté", "Prix unitaire", "Montant HT", "Total HT", "TVA 20 %", "Total TTC"},
                        List.of(it("Shooting photo produit (demi-journée)", "2", "390.00", "0.20"),
                                it("Retouche numérique, 30 images", "30", "12.00", "0.20"),
                                it("Licence d'utilisation 12 mois", "1", "450.00", "0.20")), true, true),
                new InvoiceDoc("invoice-05-bergmann", "de", "EUR", "Bergmann Haustechnik",
                        List.of("Lindenallee 7", "93047 Regensburg"),
                        List.of("Hausverwaltung Pfeiffer", "Dachauplatz 12", "93047 Regensburg"),
                        "2026-0713", LocalDate.of(2026, 10, 30),
                        new String[]{"Rechnung", "Rechnungsnr.", "Rechnungsdatum", "Pos.", "Leistung", "Anzahl", "Einzelpreis", "Betrag", "Netto", "MwSt", "Rechnungsbetrag"},
                        List.of(it("Heizungswartung Mehrfamilienhaus", "1", "385.00", "0.19"),
                                it("Ersatzteil Umwälzpumpe", "2", "142.60", "0.19"),
                                it("Fachbuch Heizungsrecht (7 % MwSt)", "1", "34.90", "0.07"),
                                it("Anfahrt", "3", "22.00", "0.19")), false, false),
                new InvoiceDoc("invoice-06-pixelhafen", "de", "EUR", "Pixelhafen Studio",
                        List.of("Speicherstadt 3", "20457 Hamburg"),
                        List.of("Jonas Reinhardt", "Birkenweg 5", "93152 Nittendorf"),
                        "PH-0098", LocalDate.of(2026, 11, 9),
                        new String[]{"Rechnung", "Rechnungsnummer", "Datum", "Pos.", "Beschreibung", "Menge", "Preis", "Summe", "Zwischensumme", "MwSt", "Gesamt"},
                        List.of(it("Logo-Design, 3 Entwürfe", "1", "750.00", "0.19"),
                                it("Schriftlizenz Desktop", "1", "89.00", "0.19"),
                                it("Treuerabatt", "1", "-75.00", "0.19"),
                                it("Versand Druckdaten (USB)", "1", "6.90", "0.19")), true, true));
    }

    private static void text(PDPageContentStream cs, PDFont f, float size, float x, float y, Color c, String s) throws IOException {
        cs.beginText();
        cs.setFont(f, size);
        cs.setNonStrokingColor(c);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private static void right(PDPageContentStream cs, PDFont f, float size, float x, float y, Color c, String s) throws IOException {
        text(cs, f, size, x - f.getStringWidth(s) / 1000f * size, y, c, s);
    }

    private static void center(PDPageContentStream cs, PDFont f, float size, float x, float y, Color c, String s) throws IOException {
        text(cs, f, size, x - f.getStringWidth(s) / 1000f * size / 2, y, c, s);
    }

    private static void line(PDPageContentStream cs, float x1, float y1, float x2, float y2, Color c) throws IOException {
        cs.setStrokingColor(c);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private static Truth drawInvoice(InvoiceDoc doc, Path path) throws IOException {
        String[] lab = doc.labels();
        String loc = doc.loc(), cur = doc.cur();

        BigDecimal subtotal = BigDecimal.ZERO;
        java.util.Map<BigDecimal, BigDecimal> netByRate = new java.util.LinkedHashMap<>();
        List<BigDecimal> nets = new ArrayList<>();
        for (Item item : doc.items()) {
            BigDecimal net = q(item.qty().multiply(item.unit()));
            nets.add(net);
            subtotal = subtotal.add(net);
            netByRate.merge(item.rate(), net, BigDecimal::add);
        }
        BigDecimal vat = BigDecimal.ZERO;
        for (var e : netByRate.entrySet()) {
            vat = vat.add(q(e.getValue().multiply(e.getKey())));
        }
        subtotal = q(subtotal);
        vat = q(vat);
        BigDecimal total = q(subtotal.add(vat));

        PDFont reg = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        Color[] accents = {new Color(0x1f4e79), new Color(0x7a2e2e), new Color(0x2f5d3a), new Color(0x333333)};
        Color accent = accents[RND.nextInt(accents.length)];

        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            float w = page.getMediaBox().getWidth(), h = page.getMediaBox().getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                float y;
                if (doc.right()) {
                    cs.setNonStrokingColor(accent);
                    cs.addRect(0, h - mm(38), w, mm(38));
                    cs.fill();
                    text(cs, bold, 20, mm(20), h - mm(20), Color.WHITE, doc.vendor());
                    text(cs, reg, 9, mm(20), h - mm(27), Color.WHITE, String.join(" · ", doc.addr()));
                    y = h - mm(58);
                    right(cs, bold, 16, w - mm(20), y, Color.BLACK, lab[0].toUpperCase());
                } else {
                    text(cs, bold, 18, mm(20), h - mm(25), accent, doc.vendor());
                    float yy = h - mm(31);
                    for (String a : doc.addr()) {
                        text(cs, reg, 9, mm(20), yy, Color.BLACK, a);
                        yy -= mm(4.5);
                    }
                    y = h - mm(58);
                    text(cs, bold, 16, mm(20), y, Color.BLACK, lab[0]);
                }
                float yy = h - mm(75);
                for (String c : doc.customer()) {
                    text(cs, reg, 9, mm(20), yy, Color.BLACK, c);
                    yy -= mm(4.5);
                }
                right(cs, reg, 10, w - mm(55), h - mm(75), Color.BLACK, lab[1] + ":");
                text(cs, reg, 10, w - mm(52), h - mm(75), Color.BLACK, doc.number());
                right(cs, reg, 10, w - mm(55), h - mm(80.5), Color.BLACK, lab[2] + ":");
                text(cs, reg, 10, w - mm(52), h - mm(80.5), Color.BLACK, fmtDate(doc.date(), loc));
                text(cs, reg, 8, w - mm(52), h - mm(86), Color.GRAY, "Kd.-Nr. " + (10000 + RND.nextInt(90000)));

                float top = h - mm(105);
                float xPos = mm(20), xDesc = mm(30), xQty = mm(118), xUnit = mm(150), xAmt = w - mm(20);
                text(cs, bold, 9, xPos, top, Color.BLACK, lab[3]);
                text(cs, bold, 9, xDesc, top, Color.BLACK, lab[4]);
                right(cs, bold, 9, xQty, top, Color.BLACK, lab[5]);
                right(cs, bold, 9, xUnit, top, Color.BLACK, lab[6]);
                right(cs, bold, 9, xAmt, top, Color.BLACK, lab[7]);
                line(cs, mm(20), top - mm(2), w - mm(20), top - mm(2), Color.BLACK);
                y = top - mm(8);
                for (int i = 0; i < doc.items().size(); i++) {
                    Item item = doc.items().get(i);
                    text(cs, reg, 9, xPos, y, Color.BLACK, String.valueOf(i + 1));
                    text(cs, reg, 9, xDesc, y, Color.BLACK, item.desc());
                    right(cs, reg, 9, xQty, y, Color.BLACK, item.qty().stripTrailingZeros().toPlainString());
                    right(cs, reg, 9, xUnit, y, Color.BLACK, fmtMoney(item.unit(), loc, cur));
                    right(cs, reg, 9, xAmt, y, Color.BLACK, fmtMoney(nets.get(i), loc, cur));
                    if (doc.grid()) {
                        line(cs, mm(20), y - mm(2.5), w - mm(20), y - mm(2.5), Color.LIGHT_GRAY);
                    }
                    y -= mm(7);
                }
                y -= mm(4);
                right(cs, reg, 10, xUnit, y, Color.BLACK, lab[8] + ":");
                right(cs, reg, 10, xAmt, y, Color.BLACK, fmtMoney(subtotal, loc, cur));
                y -= mm(6);
                right(cs, reg, 10, xUnit, y, Color.BLACK, lab[9] + ":");
                right(cs, reg, 10, xAmt, y, Color.BLACK, fmtMoney(vat, loc, cur));
                y -= mm(7);
                line(cs, xUnit - mm(38), y + mm(5), w - mm(20), y + mm(5), Color.BLACK);
                right(cs, bold, 11, xUnit, y, Color.BLACK, lab[10] + ":");
                right(cs, bold, 11, xAmt, y, Color.BLACK, fmtMoney(total, loc, cur));

                // noise at the bottom, like real invoices: bank details, register numbers, page marker
                String iban = "DE" + (10 + RND.nextInt(90)) + " " + java.util.stream.IntStream.range(0, 4)
                        .mapToObj(i -> String.valueOf(1000 + RND.nextInt(9000))).collect(Collectors.joining(" "))
                        + " " + (10 + RND.nextInt(90));
                String[] foot = {doc.vendor() + " · " + doc.addr().get(0) + " · " + doc.addr().get(1),
                        "IBAN " + iban + " · BIC TESTDEFFXXX · USt-IdNr. DE" + (100000000 + RND.nextInt(900000000)),
                        "Zahlbar innerhalb von 14 Tagen / payable within 14 days · Page 1 / 1"};
                float fy = mm(22);
                for (String f : foot) {
                    center(cs, reg, 7.5f, w / 2, fy, Color.GRAY, f);
                    fy -= mm(3.8);
                }
            }
            pdf.save(path.toFile());
        }
        return new Truth("invoices/" + doc.id() + ".pdf", doc.vendor(), doc.number(), doc.date().toString(), cur,
                doc.items().size(), subtotal.doubleValue(), vat.doubleValue(), total.doubleValue());
    }

    // ------------------------------------------------------------ receipts

    record RItem(String desc, int qty, BigDecimal unit, BigDecimal rate) {
    }

    record ReceiptDoc(String id, String loc, String cur, String vendor, List<String> addr, LocalDate date, String time,
                      boolean gross, BigDecimal tax, List<RItem> items) {
    }

    private static RItem ri(String desc, int qty, String unit, String rate) {
        return new RItem(desc, qty, d(unit), rate == null ? null : d(rate));
    }

    private static List<ReceiptDoc> receipts() {
        return List.of(
                new ReceiptDoc("receipt-01-supermarkt", "de", "EUR", "FRISCHEMARKT MEIER", List.of("Regensburger Str. 41", "93051 Regensburg"),
                        LocalDate.of(2026, 11, 14), "17:42", true, null,
                        List.of(ri("Vollmilch 3,5% 1L", 2, "1.19", "0.07"), ri("Bio Eier 10er", 1, "3.49", "0.07"), ri("Bananen 1.2kg", 1, "1.78", "0.07"),
                                ri("Waschmittel Color", 1, "7.95", "0.19"), ri("Kaffeebohnen 500g", 1, "8.99", "0.07"), ri("Mineralwasser 6x1,5L", 1, "3.54", "0.19"))),
                new ReceiptDoc("receipt-02-cafe", "de", "EUR", "Café Nordstern", List.of("Obere Bachgasse 9", "93047 Regensburg"),
                        LocalDate.of(2026, 11, 12), "09:15", true, null,
                        List.of(ri("Cappuccino", 2, "3.80", "0.19"), ri("Butterbrezel", 2, "2.20", "0.07"), ri("Apfelstrudel", 1, "4.50", "0.07"))),
                new ReceiptDoc("receipt-03-tankstelle", "de", "EUR", "STERN TANKSTELLE", List.of("Industriering 2", "93055 Regensburg"),
                        LocalDate.of(2026, 11, 10), "06:58", true, null,
                        List.of(ri("Super E10 38,42 L", 1, "67.36", "0.19"), ri("Scheibenwischer-Wasser", 1, "4.99", "0.19"), ri("Kaffee to go", 1, "2.60", "0.19"))),
                new ReceiptDoc("receipt-04-diner", "us", "USD", "Route 9 Diner", List.of("4420 Main St", "Springfield, IL 62704"),
                        LocalDate.of(2026, 11, 8), "12:31", false, d("0.0725"),
                        List.of(ri("Cheeseburger Deluxe", 2, "13.50", null), ri("Fries", 1, "4.25", null), ri("Chocolate Shake", 2, "6.00", null), ri("Pie of the day", 1, "5.75", null))),
                new ReceiptDoc("receipt-05-pharmacy", "en", "GBP", "Whitmore Pharmacy", List.of("15 High Street", "Oxford OX1 4AH"),
                        LocalDate.of(2026, 11, 6), "15:20", true, null,
                        List.of(ri("Ibuprofen 400mg x24", 1, "3.29", "0.20"), ri("Vitamin D 90 tabs", 1, "7.99", "0.20"), ri("Hand cream 75ml", 2, "4.49", "0.20"), ri("Plasters assorted", 1, "2.15", "0.20"))),
                new ReceiptDoc("receipt-06-baumarkt", "de", "EUR", "BAUMARKT HOFFMANN", List.of("Gewerbepark Süd 12", "93073 Neutraubling"),
                        LocalDate.of(2026, 11, 2), "11:07", true, null,
                        List.of(ri("Akkuschrauber-Bit-Set", 1, "14.95", "0.19"), ri("Dübel 8mm 100 St.", 2, "5.49", "0.19"), ri("Wandfarbe weiß 10L", 1, "39.90", "0.19"),
                                ri("Malerkrepp 50m", 3, "2.79", "0.19"), ri("Pflanzerde 40L", 1, "6.99", "0.07"))));
    }

    private static final int W = 46; // characters per receipt line

    private static String row(String left, String right) {
        int room = W - right.length();
        String l = left.length() > room - 1 ? left.substring(0, room - 1) : left;
        return ("%-" + room + "s").formatted(l) + right;
    }

    private static String centered(String s) {
        return " ".repeat(Math.max(0, (W - s.length()) / 2)) + s;
    }

    private static String pct(BigDecimal rate) {
        return rate.movePointRight(2).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal vatIncluded(BigDecimal gross, BigDecimal rate) {
        return q(gross.subtract(gross.divide(BigDecimal.ONE.add(rate), 10, RoundingMode.HALF_UP)));
    }

    private static Truth drawReceipt(ReceiptDoc doc, Path path) throws IOException {
        String loc = doc.loc(), cur = doc.cur();
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (RItem item : doc.items()) {
            BigDecimal amt = q(item.unit().multiply(BigDecimal.valueOf(item.qty())));
            amounts.add(amt);
            sum = sum.add(amt);
        }
        BigDecimal subtotal = null, vat = BigDecimal.ZERO, total;
        java.util.Map<BigDecimal, BigDecimal> grossByRate = new java.util.TreeMap<>();
        if (doc.gross()) {
            for (int i = 0; i < doc.items().size(); i++) {
                grossByRate.merge(doc.items().get(i).rate(), amounts.get(i), BigDecimal::add);
            }
            for (var e : grossByRate.entrySet()) {
                vat = vat.add(vatIncluded(e.getValue(), e.getKey()));
            }
            total = sum;
        } else {
            subtotal = sum;
            vat = q(sum.multiply(doc.tax()));
            total = sum.add(vat);
        }

        List<String> lines = new ArrayList<>();
        lines.add(centered(doc.vendor()));
        doc.addr().forEach(a -> lines.add(centered(a)));
        lines.add("");
        lines.add(row(fmtDate(doc.date(), loc), doc.time()));
        lines.add("-".repeat(W));
        for (int i = 0; i < doc.items().size(); i++) {
            RItem item = doc.items().get(i);
            lines.add(row(item.desc(), fmtMoney(amounts.get(i), loc, cur)));
            if (item.qty() > 1) {
                lines.add("   " + item.qty() + " x " + fmtMoney(item.unit(), loc, cur));
            }
        }
        lines.add("-".repeat(W));
        if (subtotal != null) {
            lines.add(row("SUBTOTAL", fmtMoney(subtotal, loc, cur)));
            lines.add(row("TAX " + doc.tax().movePointRight(2).stripTrailingZeros().toPlainString() + "%", fmtMoney(vat, loc, cur)));
            lines.add(row("TOTAL", fmtMoney(total, loc, cur)));
        } else {
            lines.add(row(loc.equals("de") ? "SUMME" : "TOTAL", fmtMoney(total, loc, cur)));
            lines.add("");
            for (var e : grossByRate.entrySet()) {
                lines.add(row((loc.equals("de") ? "MwSt " : "VAT ") + pct(e.getKey()) + "%" + (loc.equals("de") ? " inkl." : " incl."),
                        fmtMoney(vatIncluded(e.getValue(), e.getKey()), loc, cur)));
            }
        }
        lines.add("");
        lines.add(centered(loc.equals("de") ? "Vielen Dank für Ihren Einkauf!" : "Thank you, see you soon!"));
        lines.add(centered("TSE-Nr " + (1_000_000_000L + RND.nextInt(900_000_000))));
        lines.add(centered("Beleg " + (1000 + RND.nextInt(9000)) + "  Kasse " + (1 + RND.nextInt(4))));

        int lh = 28, wPx = 700, hPx = lh * lines.size() + 120;
        BufferedImage paper = new BufferedImage(wPx, hPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = paper.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(246, 244, 238));
        g.fillRect(0, 0, wPx, hPx);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 22));
        g.setColor(new Color(40, 40, 40));
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), 24, 50 + 20 + i * lh);
        }
        // fading thermal print: a faint horizontal band
        g.setColor(new Color(246, 244, 238, 90));
        g.fillRect(0, (int) (hPx * 0.55), wPx, (int) (hPx * 0.07));
        g.dispose();

        double angle = (RND.nextDouble() * 6.4) - 3.2;
        BufferedImage bg = new BufferedImage(wPx + 160, hPx + 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D bgG = bg.createGraphics();
        bgG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        bgG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        bgG.setColor(new Color(92, 74, 60));
        bgG.fillRect(0, 0, bg.getWidth(), bg.getHeight());
        AffineTransform at = AffineTransform.getRotateInstance(Math.toRadians(angle), bg.getWidth() / 2.0, bg.getHeight() / 2.0);
        at.translate(80, 60);
        bgG.drawImage(paper, at, null);
        bgG.dispose();
        for (int yy = 0; yy < bg.getHeight(); yy++) {
            for (int xx = 0; xx < bg.getWidth(); xx++) {
                int rgb = bg.getRGB(xx, yy);
                int n = (int) (RND.nextGaussian() * 9);
                int r = Math.clamp(((rgb >> 16) & 0xff) + n, 0, 255);
                int gg = Math.clamp(((rgb >> 8) & 0xff) + n, 0, 255);
                int b = Math.clamp((rgb & 0xff) + n, 0, 255);
                bg.setRGB(xx, yy, (r << 16) | (gg << 8) | b);
            }
        }
        float[] k = {1 / 16f, 2 / 16f, 1 / 16f, 2 / 16f, 4 / 16f, 2 / 16f, 1 / 16f, 2 / 16f, 1 / 16f};
        BufferedImage blurred = new ConvolveOp(new Kernel(3, 3, k), ConvolveOp.EDGE_NO_OP, null).filter(bg, null);
        ImageIO.write(blurred, "png", path.toFile());

        return new Truth("receipts/" + doc.id() + ".png", doc.vendor(), null, doc.date().toString(), cur,
                doc.items().size(), subtotal == null ? null : subtotal.doubleValue(), vat.doubleValue(), total.doubleValue());
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path out = Path.of(args.length > 0 ? args[0] : "samples");
        Files.createDirectories(out.resolve("invoices"));
        Files.createDirectories(out.resolve("receipts"));
        List<Truth> truth = new ArrayList<>();
        for (InvoiceDoc doc : invoices()) {
            truth.add(drawInvoice(doc, out.resolve("invoices").resolve(doc.id() + ".pdf")));
        }
        for (ReceiptDoc doc : receipts()) {
            truth.add(drawReceipt(doc, out.resolve("receipts").resolve(doc.id() + ".png")));
        }
        ObjectMapper mapper = ObjectMapper.getDefault();
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < truth.size(); i++) {
            json.append("  ").append(mapper.writeValueAsString(truth.get(i))).append(i < truth.size() - 1 ? ",\n" : "\n");
        }
        json.append("]\n");
        Files.writeString(out.resolve("ground-truth.json"), json.toString());
        System.out.println("wrote " + truth.size() + " documents to " + out.toAbsolutePath());
    }
}

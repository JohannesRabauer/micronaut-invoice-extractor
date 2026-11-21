# ChatGPT image prompt: realistic receipt photo

Image generators are good at the look of a photo and bad at exact numbers. So the prompt below
spells out every printed line, and you check the result against the list before using it.

## Prompt (copy everything in the block)

```
A realistic smartphone photograph of a thermal paper cash register receipt from a German bakery,
lying on a dark wooden table, taken from slightly above at a small angle, a little off-center.
Natural window light from the left, soft shadows, shallow depth of field, the receipt paper is
slightly curled and has one soft crease. The printing is grey-black thermal print in a monospaced
dot-matrix receipt font, a bit faded in places. Not a scan, not a mockup, no hands, no coins,
no other objects except a blurred coffee cup at the edge of the frame.

The receipt must show exactly this text, line by line, nothing else, no extra words, no
watermark. Every character and number has to be sharp and readable:

        Bäckerei Hartmann
        Kirchplatz 3
        93047 Regensburg

18.11.2026                    08:12
----------------------------------------
2x Laugenbrezel      1,10        2,20 B
1x Dinkelbrot                    4,20 B
3x Butterhörnchen    1,30        3,90 B
1x Kaffee                        2,50 A
----------------------------------------
SUMME EUR                       12,80
Bar                             12,80

MwSt 7% inkl.                    0,67
MwSt 19% inkl.                   0,40

        Vielen Dank für Ihren Einkauf!
```

Portrait orientation, 4:5, high resolution.

## After the image comes back

1. Zoom in and compare every line with the list above. If a number or word is wrong, ask for the
   same image again ("Same photo, but the receipt text must be exactly as listed, the line
   'SUMME EUR 12,80' was wrong") or regenerate. A wrong printed number is fine **only** if you
   change the ground truth to what is actually printed.
2. Save it as `samples/photos/receipt-photo-01-baeckerei.jpg`.
3. Add this entry to `samples/ground-truth.json`:

```json
{
  "file": "photos/receipt-photo-01-baeckerei.jpg",
  "vendor": "Bäckerei Hartmann",
  "invoiceNumber": null,
  "date": "2026-11-18",
  "currency": "EUR",
  "items": 4,
  "subtotal": null,
  "vat": 1.07,
  "total": 12.8
}
```

4. `python scripts/evaluate.py photo` scores only the photos.

## Variations for more samples (change the text block, keep the photo description)

- A crumpled restaurant receipt with a handwritten tip and signature line (USD, tax and tip lines).
- A long supermarket receipt with 12 items, folded once, with a coupon discount line (negative amount).
- A fuel receipt photographed at night with flash, glare on the paper, litres and price per litre.
- An invoice-sized A5 paper receipt held in one hand, slightly out of focus at the edges.

# SPDX-FileCopyrightText: 2026 Intensivate, Inc.
# SPDX-License-Identifier: BSD-2-Clause

# Generate a deterministic synthetic word list for anagram.pl.
#
# A real dictionary is someone else's copyrighted content.  These are drawn
# from a fixed eight-letter pool by a fixed linear congruential sequence, so
# every machine builds a byte-identical list (high bits of the LCG are used;
# the low bits of a power-of-two-modulus LCG have short periods).  Drawing from a small pool with
# short words is deliberate: it produces genuine anagram classes with several
# members each, and words with doubled letters, so the grouping and the regex
# passes in anagram.pl have real work to do.
#
#   awk -v n=2000 -f mkwords.awk > words.txt
BEGIN {
    if (n == 0) n = 2000;
    split("a e i l n r s t", pool, " ");
    seed = 12345;
    for (i = 0; i < n; i++) {
        seed = (1103515245 * seed + 12345) % 2147483648;
        len = 4 + (int(seed / 4096) % 2);
        word = "";
        for (j = 0; j < len; j++) {
            seed = (1103515245 * seed + 12345) % 2147483648;
            word = word pool[1 + (int(seed / 65536) % 8)];
        }
        print word;
    }
}

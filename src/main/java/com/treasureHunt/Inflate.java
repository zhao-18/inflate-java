package com.treasureHunt;

import java.util.Arrays;

/*
 * Deflate method was originally defined by Phil Katz in PKWARE’s archiving tool PKZIP 2.x.
 * It is a combination of the LZ77 algorithm and Huffman encoding.
 *
 * Raw Data ===[LZ77 encoding]===[Huffman Encoding]===> Compressed Data
 *
 * Inflate method is the other way around - decode and restore the original raw data that was
 * encoded by deflate method.
 *
 * Compressed Data ===[Huffman Decoding]===[LZ77 decoding]===> Raw Data
 *
 * ## LZ77
 *
 * If you know what they are, you may skip this
 *
 * LZ77 is a dictionary based lossless compression algorithm. It is also known as LZ1.
 *
 * The basic idea of dictionary based algorithms is to replace an occurrence of a particular
 * sequence of bytes in data with a reference to a previous occurrence of that sequence.
 * Rough overview of LZ77 compression algorithm are as follows.
 *
 * 1. Initialize the sliding window with the first symbols of the input.
 * 2. Find the longest sequence of symbols in the search buffer that matches the
 *    symbols in the look-ahead buffer.
 * 3. Output a <length:offset:char> tuple where length is length of encoding string
 *    (starting with 0), offset is where the match can be found in search buffer,
 *    and char is the last character that is different from match.
 * 4. If the look-ahead buffer is empty, we are done. Otherwise, move the sliding
 *    window as many symbols “to the right” as the length of the previous match,
 *    and repeat from step 2.
 *
 * Example of compressing "aaaabaaabbba" step by step with search buffer of size 4
 * and a look-ahead buffer of size 4.
 *
 * Search Buffer = {}
 * Look-ahead Buffer = ()
 * Window = []
 *
 *
 *  Sliding Window                 | Next to encode | <length:offset:char>
 * --------------------------------+----------------+-----------------------
 *  [{}(a a a a)] b a a a b b b a  | a              | <0:0:a>
 *  [{a} (a a a b)] a a a b b b a  | aaab           | <3:0:b>
 *  a [{a a a b} (a a a b)] b b a  | aaab           | <3:0:b>
 *  a a a a b [{a a a b} (b b a)]  | bba            | <2:3:a>
 *  a a a a b a a a [{b b b a}()]  |                |
 *
 * Result = <0:0:a> <3:0:b> <3:0:b> <2:3:a>
 *
 *
 *
 * When decompressing, LZ77 reconstruct the search buffer on the fly. In our case we
 * do it character by character, because some of the pointers point to symbols that
 * are yet to be decompressed.
 *
 * Item in parentheses are the one being processed
 *
 *
 *  To Decompress                     | Constructed search buffer | Result
 * -----------------------------------+---------------------------+--------------
 *  (<0:0:a>)<3:0:b> <3:0:b> <2:3:a>  |                           | a
 *          (<3:0:b>)<3:0:b> <2:3:a>  | a                         | aaaab
 *                  (<3:0:b>)<2:3:a>  | aaab                      | aaaabaaab
 *                          (<2:3:a>) | aaab                      | aaaabaaabbba
 *
 * Result = aaaabaaabbba
 *
 *
 * ## Huffman encoding
 *
 * I am sure you know Huffman encoding but here is a recap.
 *
 * Huffman encoding is a statistical compression method. It encodes data symbols
 * (such as characters) with variable-length codes, and lengths of the codes are
 * based on the frequencies of corresponding symbols.
 *
 * Huffman encoding has two properties:
 *
 * 1. Codes for more frequently occurring data symbols are shorter than codes for
 *    less frequently occurring data symbols.
 * 2. Each code can be uniquely decoded. This requires the codes to be prefix codes,
 *    meaning any code for one symbol is not a prefix of codes for other symbols.
 *    For example, if code “0” is used for symbol “A”, then code “01” cannot be used
 *    for symbol “B” as code “0” is a prefix of code “01”. The prefix property guarantees
 *    when decoding there is no ambiguity in determining where the symbol boundaries are.
 *
 *
 * In this treasure hunt, you must finish the implementation of huffman decoding and LZ77 decoding
 * The todos are located in corresponding function, decode_symbol and inflate_block_data
 */

public class Inflate {
    public String uncompress(byte[] input) {
        data d = new data(input, 0, 0, 0, 0);
        int bfinal;

        do {
            int btype;
            int res;

            /* Read final block flag */
            bfinal = getbits(d, 1);

            /* Read block type (2 bits) */
            btype = getbits(d, 2);

            /* Decompress block */
            res = switch (btype) {
                case 0 -> inflate_uncompressed_block(d); // Decompress uncompressed block
                case 1 -> inflate_fixed_block(d); // Decompress block with fixed Huffman trees
                case 2 -> inflate_dynamic_block(d); // Decompress block with dynamic Huffman trees
                default -> throw new RuntimeException("Unsupported compression type");
            };

            if (res < 0) throw new RuntimeException("Unable to inflate");
        } while (bfinal == 0);

        /* Check for overflow in bit reader */
        if (d.overflow != 0) throw new RuntimeException("Overflow occurred");

        return d.result.toString();
    }

    // Huffman encoding tree data
    private static class tree {
        public tree() {
            counts = new int[16];
            symbols = new int[288];
        }
        public int[] counts; /* Number of codes with a given length */
        public int[] symbols; /* Symbols sorted by code */
        public int max_sym = -1;
    }

    private static class data {
        public data(byte[] source, int source_offset, int tag, int bitcount, int overflow) {
            this.source = source;
            this.source_offset = source_offset;
            this.tag = tag;
            this.bitcount = bitcount;
            this.overflow = overflow;
            result = new StringBuilder();
        }
        public byte[] source;
        public int source_offset;
        public int tag;
        public int bitcount;
        public int overflow;
        public StringBuilder result;
        public tree ltree = new tree();
        public tree dtree = new tree();
    }

    private static int read_le16(int p, int q) {
        return p | (q << 8);
    }

    private static void build_fixed_trees(tree lt, tree dt) {
        int i;

        /* Build fixed literal/length tree */
        for (i = 0; i < 16; i++) {
            lt.counts[i] = 0;
        }

        lt.counts[7] = 24;
        lt.counts[8] = 152;
        lt.counts[9] = 112;

        for (i = 0; i < 24; i++) {
            lt.symbols[i] = 256 + i;
        }

        for (i = 0; i < 144; i++) {
            lt.symbols[24 + i] = i;
        }

        for (i = 0; i < 8; i++) {
            lt.symbols[24 + 144 + i] = 280 + i;
        }

        for (i = 0; i < 112; i++) {
            lt.symbols[24 + 144 + 8 + i] = 144 + i;
        }

        lt.max_sym = 285;

        /* Build fixed distance tree */
        for (i = 0; i < 16; i++) {
            dt.counts[i] = 0;
        }

        dt.counts[5] = 32;

        for (i = 0; i < 32; i++) {
            dt.symbols[i] = i;
        }

        dt.max_sym = 29;
    }

    private static int build_tree(tree t, int[] lengths, int num) {
        int[] offs = new int[16];
        int i, num_codes, available;

        assert num <= 288;

        for (i = 0; i < 16; i++) {
            t.counts[i] = 0;
        }

        t.max_sym = -1;

        /* Count number of codes for each non-zero length */
        for (i = 0; i < num; i++) {
            assert lengths[i] <= 15;
            if (lengths[i] != 0) {
                t.counts[lengths[i]]++;
                if (i > t.max_sym) {
                    t.max_sym = i;
                }
            }
        }

        /* Compute offset table for distribution sort */
        for (available = 1, num_codes = 0, i = 0; i < 16; ++i) {
            int used = t.counts[i];

            /* Check length contains no more codes than available */
            if (used > available) return -1;

            available = 2 * (available - used);

            offs[i] = num_codes;
            num_codes += used;
        }

        /*
         * Check all codes were used, or for the special case of only one
         * code that it has length 1
         */
        if ((num_codes > 1 && available > 0) ||
                (num_codes == 1 && t.counts[1] != 1)) {
            return -1;
        }

        /* Fill in symbols sorted by code */
        for (i = 0; i < num; ++i) {
            if (lengths[i] != 0) {
                t.symbols[offs[lengths[i]]++] = i;
            }
        }

        /*
         * For the special case of only one code (which will be 0) add a
         * code 1 which results in a symbol that is too large
         */
        if (num_codes == 1) {
            t.counts[1] = 2;
            t.symbols[1] = t.max_sym + 1;
        }

        return 1;
    }

    private static void refill(data d, int num) {
        if (!(num >= 0 && num <= 32)) throw new IllegalArgumentException("num must be within [0, 32]");

        /* Read bytes until at least num bits available */
        while (d.bitcount < num) {
            if (d.source_offset < d.source.length) {
                d.tag |= (d.source[d.source_offset] & 0xFF) << d.bitcount;
                d.source_offset++;
            } else {
                d.overflow = 1;
            }
            d.bitcount += 8;
        }

        if (d.bitcount > 32) throw new RuntimeException("bitcount exceeded 32");
    }

    private static int getbits_no_refill(data d, int num) {
        int bits;
        if (num < 0 || num > d.bitcount) throw new IllegalArgumentException("num must be within [0, d.bitcount] where d.bitcount = " + d.bitcount);

        /* Get bits from tag */
        bits = d.tag & ((1 << num) - 1);

        /* Remove bits from tag */
        d.tag >>= num;
        d.bitcount -= num;

        return bits;
    }

    private static int getbits(data d, int num) {
        refill(d, num);
        return getbits_no_refill(d, num);
    }

    private static int getbits_base(data d, int num, int base) {
        return base + ((num != 0) ? getbits(d, num) : 0);
    }

    // Huffman decoding
    private static int decode_symbol(data d, tree t) {
        int code = 0, first = 0, index = 0;

        for (int i = 1; i <= 15; i++) {

            code |= getbits(d, 1); // Read one bit at a time

            int count = t.counts[i]; // Number of codes of this length
            if (code - first < count) { // Make sure the range is correct
                return t.symbols[index + (code - first)];
            }

            index += count;
            code *= 2;
            first = (first + count) * 2;
        }

        return -1; // Error: invalid symbol
    }

    private static int decode_trees(data d) {
        tree lt = d.ltree;
        tree dt = d.dtree;

        int[] lengths = new int[288 + 32];

        /* Special ordering of code length codes */
        int[] clcidx = {
                16, 17, 18, 0,  8, 7,  9, 6, 10, 5,
                11, 4, 12, 3, 13, 2, 14, 1, 15
        };

        int hlit, hdist, hclen;
        int i, num, length;
        int res;

        /* Get 5 bits HLIT (257-286) */
        hlit = getbits_base(d, 5, 257);

        /* Get 5 bits HDIST (1-32) */
        hdist = getbits_base(d, 5, 1);

        /* Get 4 bits HCLEN (4-19) */
        hclen = getbits_base(d, 4, 4);

        /*
         * The RFC limits the range of HLIT to 286, but lists HDIST as range
         * 1-32, even though distance codes 30 and 31 have no meaning. While
         * we could allow the full range of HLIT and HDIST to make it possible
         * to decode the fixed trees with this function, we consider it an
         * error here.
         *
         * See also: https://github.com/madler/zlib/issues/82
         */
        if (hlit > 286 || hdist > 30) {
            return -1;
        }

        for (i = 0; i < 19; i++) {
            lengths[i] = 0;
        }

        /* Read code lengths for code length alphabet */
        for (i = 0; i < hclen; i++) {
            /* Get 3 bits code length (0-7) */
            int clen = getbits(d, 3);
            lengths[clcidx[i]] = clen;
        }

        /* Build code length tree (in literal / length tree to save space) */
        res = build_tree(lt, lengths, 19);
        if (res < 0) return res;

        /* Check code length tree is not empty */
        if (lt.max_sym == -1) return -1;

        /* Decode code lengths for the dynamic trees */
        for (num = 0; num < hlit + hdist;) {
            int sym = decode_symbol(d, lt);

            if (sym > lt.max_sym) return 1;

            switch (sym) {
                case 16:
                    /* Copy previous code length 3-6 times (read 2 bits) */
                    if (num == 0) {
                        return -1;
                    }
                    sym = lengths[num - 1];
                    length = getbits_base(d, 2, 3);
                    break;
                case 17:
                    /* Repeat code length 0 for 3-10 times (read 3 bits) */
                    sym = 0;
                    length = getbits_base(d, 3, 3);
                    break;
                case 18:
                    /* Repeat code length 0 for 11-138 times (read 7 bits) */
                    sym = 0;
                    length = getbits_base(d, 7, 11);
                    break;
                default:
                    /* Values 0-15 represent the actual code lengths */
                    length = sym;
                    break;
            }

            if (length > hlit + hdist - num) {
                return -1;
            }

            while (length-- != 0) {
                lengths[num++] = sym;
            }
        }

        /* Check EOB symbol is present */
        if (lengths[256] == 0) {
            return -1;
        }

        /* Build dynamic trees */
        res = build_tree(lt, lengths, hlit);

        if (res > 0) {
            return res;
        }

        res = build_tree(dt, Arrays.copyOfRange(lengths, hlit, lengths.length), hdist);

        if (res > 0) {
            return res;
        }

        return 1;
    }

    private static int inflate_block_data(data d) {
        tree lt = d.ltree;
        tree dt = d.dtree;

        /* Extra bits and base tables for length codes */
        int[] length_bits = {
                0, 0, 0, 0, 0, 0, 0, 0, 1, 1,
                1, 1, 2, 2, 2, 2, 3, 3, 3, 3,
                4, 4, 4, 4, 5, 5, 5, 5, 0, 127
        };

        int[] length_base = {
                3,  4,  5,   6,   8,   8,   9,  10,  11,  13,
                15, 17, 19,  23,  27,  31,  35,  43,  51,  59,
                67, 83, 99, 115, 131, 163, 195, 227, 258,   0
        };

        /* Extra bits and base tables for distance codes */
        int[] dist_bits = {
                0, 0,  0,  0,  1,  1,  2,  2,  3,  3,
                4, 4,  5,  5,  6,  6,  7,  7,  8,  8,
                9, 9, 10, 10, 11, 11, 12, 12, 13, 13
        };

        int[] dist_base = {
                1,    2,    3,    4,    5,    7,    9,    13,    17,    25,
                33,   49,   65,   97,  129,  193,  257,   385,   513,   769,
                1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
        };

        for (;;) {
            int sym = decode_symbol(d, lt);

            /* Check for overflow in bit reader */
            if (d.overflow != 0) return -1;

            if (sym < 256) {
                d.result.append((char) sym);
            } else {
                int length, dist, offset;
                int i;

                /* Check for end of blocks */
                if (sym == 256) return 1;

                /* Check sym is within range and distance tree is not empty */
                if (sym > lt.max_sym || sym - 257 > 28 || dt.max_sym == -1) return -1;


                sym -= 257;

                /* Possibly get more bits from length code */
                length = getbits_base(d, length_bits[sym], length_base[sym]);

                dist = decode_symbol(d, dt);

                /* Check dist is within range */
                if (dist > dt.max_sym || dist > 29) return -1;

                /* Possibly get more bits from distance code */
                offset = getbits_base(d, dist_bits[dist], dist_base[dist]);

                if (offset > d.result.length()) return -1;

                while (length-- > 0) {
                    d.result.append(d.result.charAt(d.result.length() - offset));
                }
            }
        }
    }

    private static int inflate_uncompressed_block(data d) {
        int length;
        int invlength;

        if (d.source.length - d.source_offset < 4) return -1;

        /* Get length */
        length = read_le16(d.source[d.source_offset] & 0xFF, d.source[d.source_offset + 1] & 0xFF);

        /* Get one's compliment of length */
        invlength = read_le16(d.source[d.source_offset + 2] & 0xFF, d.source[d.source_offset + 3] & 0xFF);

        /* Check length */
        if (length != (~invlength & 0x0000FFFF)) return -1;

        d.source_offset += 4;

        if (d.source.length - d.source_offset < length) return -1;

        /* Copy block */
        while (length != 0) {
            d.result.append((char) (d.source[d.source_offset] & 0xFF));
            length--;
            d.source_offset++;
        }

        d.tag = 0;
        d.bitcount = 0;

        return 1;
    }

    private static int inflate_fixed_block(data d) {
        /* Build fixed Huffman trees */
        build_fixed_trees(d.ltree, d.dtree);

        /* Decode block using fixed trees */
        return inflate_block_data(d);
    }

    private static int inflate_dynamic_block(data d) {
        /* Decode trees from stream */
        int res = decode_trees(d);

        if (res < 0) return res;

        /* Decode block using decoded trees */
        return inflate_block_data(d);
    }
}

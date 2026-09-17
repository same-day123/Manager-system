package com.ruoyi.project.laboratory.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.ruoyi.common.utils.StringUtils;

/**
 * Lightweight QR Code SVG generator for short asset repair links.
 * Supports byte mode, error correction level L, QR versions 1-5.
 */
public class QrCodeUtils
{
    private static final int MIN_VERSION = 1;

    private static final int MAX_VERSION = 5;

    private static final int[] DATA_CODEWORDS = { 0, 19, 34, 55, 80, 108 };

    private static final int[] ECC_CODEWORDS = { 0, 7, 10, 15, 20, 26 };

    private static final int[][] ALIGNMENT_POSITIONS = {
            {},
            {},
            { 6, 18 },
            { 6, 22 },
            { 6, 26 },
            { 6, 30 }
    };

    private static final int[] EXP_TABLE = new int[256];

    private static final int[] LOG_TABLE = new int[256];

    static
    {
        int x = 1;
        for (int i = 0; i < 255; i++)
        {
            EXP_TABLE[i] = x;
            LOG_TABLE[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0)
            {
                x ^= 0x11D;
            }
        }
        EXP_TABLE[255] = EXP_TABLE[0];
    }

    private QrCodeUtils()
    {
    }

    public static String generateSvg(String content, String title, String subtitle)
    {
        QrCode qrCode = encode(content);
        return toSvg(qrCode, title, subtitle);
    }

    private static QrCode encode(String content)
    {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        for (int version = MIN_VERSION; version <= MAX_VERSION; version++)
        {
            int[] dataCodewords = buildDataCodewords(data, version);
            if (dataCodewords == null)
            {
                continue;
            }
            int[] eccCodewords = computeEcc(dataCodewords, ECC_CODEWORDS[version]);
            int[] codewords = new int[dataCodewords.length + eccCodewords.length];
            System.arraycopy(dataCodewords, 0, codewords, 0, dataCodewords.length);
            System.arraycopy(eccCodewords, 0, codewords, dataCodewords.length, eccCodewords.length);

            QrCode qrCode = new QrCode(version);
            qrCode.drawFunctionPatterns();
            qrCode.drawCodewords(codewords);
            qrCode.drawFormatBits(0);
            return qrCode;
        }
        throw new IllegalArgumentException("QR content is too long");
    }

    private static int[] buildDataCodewords(byte[] data, int version)
    {
        int capacityBits = DATA_CODEWORDS[version] * 8;
        BitBuffer buffer = new BitBuffer();
        buffer.appendBits(0x4, 4);
        buffer.appendBits(data.length, 8);
        for (byte value : data)
        {
            buffer.appendBits(value & 0xFF, 8);
        }
        if (buffer.size() > capacityBits)
        {
            return null;
        }

        buffer.appendBits(0, Math.min(4, capacityBits - buffer.size()));
        while (buffer.size() % 8 != 0)
        {
            buffer.appendBits(0, 1);
        }

        int pad = 0xEC;
        while (buffer.size() < capacityBits)
        {
            buffer.appendBits(pad, 8);
            pad = pad == 0xEC ? 0x11 : 0xEC;
        }
        return buffer.toCodewords(DATA_CODEWORDS[version]);
    }

    private static int[] computeEcc(int[] data, int degree)
    {
        int[] generator = buildGenerator(degree);
        int[] remainder = new int[degree];
        for (int value : data)
        {
            int factor = value ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, degree - 1);
            remainder[degree - 1] = 0;
            for (int i = 0; i < degree; i++)
            {
                remainder[i] ^= multiply(generator[i + 1], factor);
            }
        }
        return remainder;
    }

    private static int[] buildGenerator(int degree)
    {
        int[] generator = { 1 };
        for (int i = 0; i < degree; i++)
        {
            int[] next = new int[generator.length + 1];
            for (int j = 0; j < generator.length; j++)
            {
                next[j] ^= generator[j];
                next[j + 1] ^= multiply(generator[j], EXP_TABLE[i]);
            }
            generator = next;
        }
        return generator;
    }

    private static int multiply(int x, int y)
    {
        if (x == 0 || y == 0)
        {
            return 0;
        }
        return EXP_TABLE[(LOG_TABLE[x] + LOG_TABLE[y]) % 255];
    }

    private static String toSvg(QrCode qrCode, String title, String subtitle)
    {
        int border = 4;
        int scale = 8;
        int codePixels = (qrCode.size + border * 2) * scale;
        boolean hasTitle = StringUtils.isNotEmpty(title);
        boolean hasSubtitle = StringUtils.isNotEmpty(subtitle);
        int footerHeight = (hasTitle ? 24 : 0) + (hasSubtitle ? 20 : 0) + 10;
        int height = codePixels + footerHeight;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(codePixels).append(" ").append(height).append("\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>");
        for (int y = 0; y < qrCode.size; y++)
        {
            for (int x = 0; x < qrCode.size; x++)
            {
                if (qrCode.modules[y][x])
                {
                    svg.append("<rect x=\"").append((x + border) * scale)
                            .append("\" y=\"").append((y + border) * scale)
                            .append("\" width=\"").append(scale)
                            .append("\" height=\"").append(scale)
                            .append("\" fill=\"#111827\"/>");
                }
            }
        }
        int textY = codePixels + 16;
        if (hasTitle)
        {
            svg.append("<text x=\"").append(codePixels / 2).append("\" y=\"").append(textY)
                    .append("\" text-anchor=\"middle\" font-size=\"13\" font-family=\"Microsoft YaHei,Arial\"")
                    .append(" font-weight=\"700\" fill=\"#111827\">")
                    .append(escapeXml(title)).append("</text>");
            textY += 20;
        }
        if (hasSubtitle)
        {
            svg.append("<text x=\"").append(codePixels / 2).append("\" y=\"").append(textY)
                    .append("\" text-anchor=\"middle\" font-size=\"10\" font-family=\"Microsoft YaHei,Arial\"")
                    .append(" fill=\"#64748b\">")
                    .append(escapeXml(subtitle)).append("</text>");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private static String escapeXml(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static int formatBits(int mask)
    {
        int data = (1 << 3) | mask;
        int bits = data << 10;
        int generator = 0x537;
        for (int i = 14; i >= 10; i--)
        {
            if (((bits >>> i) & 1) != 0)
            {
                bits ^= generator << (i - 10);
            }
        }
        return ((data << 10) | bits) ^ 0x5412;
    }

    private static boolean getBit(int value, int index)
    {
        return ((value >>> index) & 1) != 0;
    }

    private static class QrCode
    {
        private final int version;

        private final int size;

        private final boolean[][] modules;

        private final boolean[][] functionModules;

        QrCode(int version)
        {
            this.version = version;
            this.size = version * 4 + 17;
            this.modules = new boolean[size][size];
            this.functionModules = new boolean[size][size];
        }

        void drawFunctionPatterns()
        {
            drawFinderPattern(0, 0);
            drawFinderPattern(size - 7, 0);
            drawFinderPattern(0, size - 7);
            drawTimingPatterns();
            drawAlignmentPatterns();
            reserveFormatBits();
        }

        void drawCodewords(int[] codewords)
        {
            int bitIndex = 0;
            int totalBits = codewords.length * 8;
            boolean upward = true;
            for (int right = size - 1; right >= 1; right -= 2)
            {
                if (right == 6)
                {
                    right--;
                }
                for (int vertical = 0; vertical < size; vertical++)
                {
                    int y = upward ? size - 1 - vertical : vertical;
                    for (int j = 0; j < 2; j++)
                    {
                        int x = right - j;
                        if (functionModules[y][x])
                        {
                            continue;
                        }
                        boolean dark = false;
                        if (bitIndex < totalBits)
                        {
                            dark = ((codewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) != 0;
                        }
                        if (((x + y) & 1) == 0)
                        {
                            dark = !dark;
                        }
                        modules[y][x] = dark;
                        bitIndex++;
                    }
                }
                upward = !upward;
            }
        }

        void drawFormatBits(int mask)
        {
            int bits = formatBits(mask);
            for (int i = 0; i <= 5; i++)
            {
                setFunctionModule(8, i, getBit(bits, i));
            }
            setFunctionModule(8, 7, getBit(bits, 6));
            setFunctionModule(8, 8, getBit(bits, 7));
            setFunctionModule(7, 8, getBit(bits, 8));
            for (int i = 9; i < 15; i++)
            {
                setFunctionModule(14 - i, 8, getBit(bits, i));
            }
            for (int i = 0; i < 8; i++)
            {
                setFunctionModule(size - 1 - i, 8, getBit(bits, i));
            }
            for (int i = 8; i < 15; i++)
            {
                setFunctionModule(8, size - 15 + i, getBit(bits, i));
            }
            setFunctionModule(8, size - 8, true);
        }

        private void drawFinderPattern(int left, int top)
        {
            for (int dy = -1; dy <= 7; dy++)
            {
                for (int dx = -1; dx <= 7; dx++)
                {
                    int x = left + dx;
                    int y = top + dy;
                    if (x < 0 || y < 0 || x >= size || y >= size)
                    {
                        continue;
                    }
                    boolean dark = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6
                            && (dx == 0 || dx == 6 || dy == 0 || dy == 6
                                    || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
                    setFunctionModule(x, y, dark);
                }
            }
        }

        private void drawTimingPatterns()
        {
            for (int i = 8; i < size - 8; i++)
            {
                boolean dark = i % 2 == 0;
                setFunctionModule(i, 6, dark);
                setFunctionModule(6, i, dark);
            }
        }

        private void drawAlignmentPatterns()
        {
            int[] positions = ALIGNMENT_POSITIONS[version];
            for (int y : positions)
            {
                for (int x : positions)
                {
                    if (functionModules[y][x])
                    {
                        continue;
                    }
                    drawAlignmentPattern(x, y);
                }
            }
        }

        private void drawAlignmentPattern(int centerX, int centerY)
        {
            for (int dy = -2; dy <= 2; dy++)
            {
                for (int dx = -2; dx <= 2; dx++)
                {
                    boolean dark = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                    setFunctionModule(centerX + dx, centerY + dy, dark);
                }
            }
        }

        private void reserveFormatBits()
        {
            for (int i = 0; i < 9; i++)
            {
                if (i != 6)
                {
                    setFunctionModule(8, i, false);
                    setFunctionModule(i, 8, false);
                }
            }
            for (int i = 0; i < 8; i++)
            {
                setFunctionModule(size - 1 - i, 8, false);
                setFunctionModule(8, size - 1 - i, false);
            }
            setFunctionModule(8, size - 8, true);
        }

        private void setFunctionModule(int x, int y, boolean dark)
        {
            modules[y][x] = dark;
            functionModules[y][x] = true;
        }
    }

    private static class BitBuffer
    {
        private final List<Integer> bits = new ArrayList<Integer>();

        void appendBits(int value, int length)
        {
            if (length < 0 || length > 31 || value >>> length != 0)
            {
                throw new IllegalArgumentException("Invalid bit value");
            }
            for (int i = length - 1; i >= 0; i--)
            {
                bits.add((value >>> i) & 1);
            }
        }

        int size()
        {
            return bits.size();
        }

        int[] toCodewords(int length)
        {
            int[] result = new int[length];
            for (int i = 0; i < bits.size(); i++)
            {
                result[i >>> 3] |= bits.get(i) << (7 - (i & 7));
            }
            return result;
        }
    }
}

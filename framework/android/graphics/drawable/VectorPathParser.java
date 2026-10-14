// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import java.awt.geom.Path2D;

/** Parses the SVG path commands used by Android vector drawable pathData. */
final class VectorPathParser {
    private final String data;
    private final Path2D.Float path;
    private int position;
    private float x;
    private float y;
    private float startX;
    private float startY;
    private float cubicX;
    private float cubicY;
    private float quadX;
    private float quadY;
    private char previousCommand;

    private VectorPathParser(String data, boolean evenOdd) {
        this.data = data;
        this.path = new Path2D.Float(evenOdd ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
    }

    static Path2D.Float parse(String data, boolean evenOdd) {
        if (data == null || data.isEmpty()) throw new IllegalArgumentException("empty pathData");
        return new VectorPathParser(data, evenOdd).parse();
    }

    private Path2D.Float parse() {
        char command = 0;
        while (true) {
            skipSeparators();
            if (position >= data.length()) return path;
            char next = data.charAt(position);
            if (isCommand(next)) {
                command = next;
                position++;
            } else if (command == 0) {
                throw error("expected command");
            }

            if (command == 'Z' || command == 'z') {
                path.closePath();
                x = startX;
                y = startY;
                cubicX = quadX = x;
                cubicY = quadY = y;
                previousCommand = command;
                command = 0;
                continue;
            }

            int groups = 0;
            while (hasNumber()) {
                char executed = command;
                switch (executed) {
                    case 'M': case 'm': {
                        float px = number(), py = number();
                        if (executed == 'm') { px += x; py += y; }
                        if (groups == 0) {
                            path.moveTo(px, py);
                            startX = px;
                            startY = py;
                        } else {
                            path.lineTo(px, py);
                        }
                        x = px;
                        y = py;
                        command = executed == 'm' ? 'l' : 'L';
                        break;
                    }
                    case 'L': case 'l': {
                        float px = number(), py = number();
                        if (executed == 'l') { px += x; py += y; }
                        path.lineTo(px, py);
                        x = px;
                        y = py;
                        break;
                    }
                    case 'H': case 'h': {
                        float px = number();
                        x = executed == 'h' ? x + px : px;
                        path.lineTo(x, y);
                        break;
                    }
                    case 'V': case 'v': {
                        float py = number();
                        y = executed == 'v' ? y + py : py;
                        path.lineTo(x, y);
                        break;
                    }
                    case 'C': case 'c': {
                        float x1 = number(), y1 = number(), x2 = number(), y2 = number();
                        float x3 = number(), y3 = number();
                        if (executed == 'c') {
                            x1 += x; x2 += x; x3 += x;
                            y1 += y; y2 += y; y3 += y;
                        }
                        path.curveTo(x1, y1, x2, y2, x3, y3);
                        cubicX = x2; cubicY = y2;
                        x = x3; y = y3;
                        break;
                    }
                    case 'S': case 's': {
                        float x1 = previousCommand == 'C' || previousCommand == 'c'
                                || previousCommand == 'S' || previousCommand == 's' ? 2 * x - cubicX : x;
                        float y1 = previousCommand == 'C' || previousCommand == 'c'
                                || previousCommand == 'S' || previousCommand == 's' ? 2 * y - cubicY : y;
                        float x2 = number(), y2 = number(), x3 = number(), y3 = number();
                        if (executed == 's') {
                            x2 += x; x3 += x;
                            y2 += y; y3 += y;
                        }
                        path.curveTo(x1, y1, x2, y2, x3, y3);
                        cubicX = x2; cubicY = y2;
                        x = x3; y = y3;
                        break;
                    }
                    case 'Q': case 'q': {
                        float x1 = number(), y1 = number(), x2 = number(), y2 = number();
                        if (executed == 'q') {
                            x1 += x; x2 += x;
                            y1 += y; y2 += y;
                        }
                        path.quadTo(x1, y1, x2, y2);
                        quadX = x1; quadY = y1;
                        x = x2; y = y2;
                        break;
                    }
                    case 'T': case 't': {
                        float x1 = previousCommand == 'Q' || previousCommand == 'q'
                                || previousCommand == 'T' || previousCommand == 't' ? 2 * x - quadX : x;
                        float y1 = previousCommand == 'Q' || previousCommand == 'q'
                                || previousCommand == 'T' || previousCommand == 't' ? 2 * y - quadY : y;
                        float x2 = number(), y2 = number();
                        if (executed == 't') { x2 += x; y2 += y; }
                        path.quadTo(x1, y1, x2, y2);
                        quadX = x1; quadY = y1;
                        x = x2; y = y2;
                        break;
                    }
                    case 'A': case 'a': {
                        float rx = number(), ry = number(), rotation = number();
                        boolean largeArc = number() != 0;
                        boolean sweep = number() != 0;
                        float x2 = number(), y2 = number();
                        if (executed == 'a') { x2 += x; y2 += y; }
                        arcTo(rx, ry, rotation, largeArc, sweep, x2, y2);
                        break;
                    }
                    default:
                        throw error("unsupported command " + executed);
                }
                if (!isCurveCommand(executed)) {
                    cubicX = quadX = x;
                    cubicY = quadY = y;
                }
                previousCommand = executed;
                groups++;
                skipSeparators();
                if (position < data.length() && isCommand(data.charAt(position))) break;
            }
            if (groups == 0) throw error("missing command parameters");
        }
    }

    private void arcTo(float rx, float ry, float rotation, boolean largeArc, boolean sweep,
            float endX, float endY) {
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx == 0 || ry == 0 || (x == endX && y == endY)) {
            path.lineTo(endX, endY);
            x = endX;
            y = endY;
            cubicX = quadX = x;
            cubicY = quadY = y;
            return;
        }

        double phi = Math.toRadians(rotation % 360.0);
        double cos = Math.cos(phi), sin = Math.sin(phi);
        double halfX = (x - endX) * 0.5, halfY = (y - endY) * 0.5;
        double xPrime = cos * halfX + sin * halfY;
        double yPrime = -sin * halfX + cos * halfY;
        double rx2 = (double) rx * rx, ry2 = (double) ry * ry;
        double scale = xPrime * xPrime / rx2 + yPrime * yPrime / ry2;
        if (scale > 1) {
            double factor = Math.sqrt(scale);
            rx *= factor;
            ry *= factor;
            rx2 = (double) rx * rx;
            ry2 = (double) ry * ry;
        }

        double denominator = rx2 * yPrime * yPrime + ry2 * xPrime * xPrime;
        double numerator = Math.max(0, rx2 * ry2 - denominator);
        double sign = largeArc == sweep ? -1 : 1;
        double factor = denominator == 0 ? 0 : sign * Math.sqrt(numerator / denominator);
        double centerPrimeX = factor * rx * yPrime / ry;
        double centerPrimeY = -factor * ry * xPrime / rx;
        double centerX = cos * centerPrimeX - sin * centerPrimeY + (x + endX) * 0.5;
        double centerY = sin * centerPrimeX + cos * centerPrimeY + (y + endY) * 0.5;
        double ux = (xPrime - centerPrimeX) / rx;
        double uy = (yPrime - centerPrimeY) / ry;
        double vx = (-xPrime - centerPrimeX) / rx;
        double vy = (-yPrime - centerPrimeY) / ry;
        double startAngle = Math.atan2(uy, ux);
        double sweepAngle = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        if (!sweep && sweepAngle > 0) sweepAngle -= Math.PI * 2;
        else if (sweep && sweepAngle < 0) sweepAngle += Math.PI * 2;

        int segments = Math.max(1, (int) Math.ceil(Math.abs(sweepAngle) / (Math.PI * 0.5)));
        double step = sweepAngle / segments;
        double angle = startAngle;
        for (int i = 0; i < segments; i++) {
            double next = angle + step;
            double alpha = 4.0 / 3.0 * Math.tan((next - angle) / 4.0);
            double[] p0 = ellipsePoint(centerX, centerY, rx, ry, cos, sin, angle);
            double[] p1 = ellipsePoint(centerX, centerY, rx, ry, cos, sin, next);
            double[] d0 = ellipseDerivative(rx, ry, cos, sin, angle);
            double[] d1 = ellipseDerivative(rx, ry, cos, sin, next);
            path.curveTo((float) (p0[0] + alpha * d0[0]), (float) (p0[1] + alpha * d0[1]),
                    (float) (p1[0] - alpha * d1[0]), (float) (p1[1] - alpha * d1[1]),
                    (float) p1[0], (float) p1[1]);
            angle = next;
        }
        x = endX;
        y = endY;
        cubicX = quadX = x;
        cubicY = quadY = y;
    }

    private static double[] ellipsePoint(double cx, double cy, double rx, double ry,
            double cos, double sin, double angle) {
        double px = rx * Math.cos(angle), py = ry * Math.sin(angle);
        return new double[]{cx + cos * px - sin * py, cy + sin * px + cos * py};
    }

    private static double[] ellipseDerivative(double rx, double ry, double cos, double sin,
            double angle) {
        double dx = -rx * Math.sin(angle), dy = ry * Math.cos(angle);
        return new double[]{cos * dx - sin * dy, sin * dx + cos * dy};
    }

    private float number() {
        skipSeparators();
        int start = position;
        if (position < data.length() && (data.charAt(position) == '-' || data.charAt(position) == '+')) position++;
        boolean digits = false;
        while (position < data.length() && Character.isDigit(data.charAt(position))) {
            position++;
            digits = true;
        }
        if (position < data.length() && data.charAt(position) == '.') {
            position++;
            while (position < data.length() && Character.isDigit(data.charAt(position))) {
                position++;
                digits = true;
            }
        }
        if (!digits) throw error("expected number");
        if (position < data.length() && (data.charAt(position) == 'e' || data.charAt(position) == 'E')) {
            position++;
            if (position < data.length() && (data.charAt(position) == '-' || data.charAt(position) == '+')) position++;
            int exponentStart = position;
            while (position < data.length() && Character.isDigit(data.charAt(position))) position++;
            if (position == exponentStart) throw error("invalid exponent");
        }
        float value;
        try {
            value = Float.parseFloat(data.substring(start, position));
        } catch (NumberFormatException e) {
            throw error("invalid number");
        }
        if (!Float.isFinite(value)) throw error("non-finite number");
        return value;
    }

    private boolean hasNumber() {
        skipSeparators();
        if (position >= data.length()) return false;
        char c = data.charAt(position);
        return c == '+' || c == '-' || c == '.' || Character.isDigit(c);
    }

    private void skipSeparators() {
        while (position < data.length()) {
            char c = data.charAt(position);
            if (c == ',' || Character.isWhitespace(c)) position++;
            else return;
        }
    }

    private static boolean isCommand(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isCurveCommand(char c) {
        return c == 'C' || c == 'c' || c == 'S' || c == 's' || c == 'Q' || c == 'q'
                || c == 'T' || c == 't' || c == 'A' || c == 'a';
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + position + " in pathData");
    }
}

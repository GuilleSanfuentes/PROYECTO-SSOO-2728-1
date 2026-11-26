package avilaos.vista.gui;

import java.awt.Color;

/** Color estable de cada archivo en el mapa del disco y en la tabla de asignación (depende solo de su ID). */
public final class ColoresArchivo {

    /** Color de los bloques de procesos suspendidos en el área de intercambio. */
    public static final Color SWAP_OCUPADO = new Color(124, 92, 170);
    /** Color de los bloques reservados por una creación que el disco aún no termina. */
    public static final Color RESERVADO = new Color(150, 156, 166);
    /** Fondo de un bloque libre de archivos. */
    public static final Color LIBRE = new Color(244, 246, 248);
    /** Fondo de un bloque libre del área de intercambio. */
    public static final Color SWAP_LIBRE = new Color(236, 231, 245);

    private static final Color[] PALETA = {
        new Color(31, 119, 180), new Color(255, 127, 14), new Color(44, 160, 44), new Color(214, 39, 40),
        new Color(23, 190, 207), new Color(188, 189, 34), new Color(227, 119, 194), new Color(140, 86, 75),
        new Color(57, 106, 177), new Color(218, 124, 48), new Color(62, 150, 81), new Color(204, 37, 41),
        new Color(83, 81, 84), new Color(107, 76, 154), new Color(146, 36, 40), new Color(148, 139, 61)
    };

    private ColoresArchivo() {
    }

    /** Color del archivo con ese ID; siempre el mismo para el mismo ID. */
    public static Color de(int idArchivo) {
        return PALETA[Math.floorMod(idArchivo, PALETA.length)];
    }

    /** Negro o blanco, el que se lea mejor sobre el fondo indicado. */
    public static Color textoSobre(Color fondo) {
        double luz = 0.299 * fondo.getRed() + 0.587 * fondo.getGreen() + 0.114 * fondo.getBlue();
        return luz > 150 ? new Color(33, 37, 41) : Color.WHITE;
    }

    /** Cantidad de colores distintos antes de repetir. */
    public static int cantidad() {
        return PALETA.length;
    }
}

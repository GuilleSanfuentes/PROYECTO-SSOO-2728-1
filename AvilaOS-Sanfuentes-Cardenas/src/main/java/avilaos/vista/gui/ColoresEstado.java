package avilaos.vista.gui;

import avilaos.enums.EstadoProceso;
import java.awt.Color;

/** Paleta de la interfaz: un color por estado de proceso y colores de semáforo para las barras. */
final class ColoresEstado {

    static final Color VERDE = new Color(46, 160, 67);
    static final Color AMARILLO = new Color(230, 170, 20);
    static final Color ROJO = new Color(215, 58, 73);
    static final Color GRIS = new Color(140, 148, 158);
    static final Color AZUL = new Color(70, 130, 200);
    static final Color NARANJA = new Color(235, 125, 40);
    static final Color TEXTO = new Color(33, 37, 41);
    static final Color PISTA = new Color(232, 235, 239);

    private ColoresEstado() {
    }

    /** Color intenso del estado (verde ejecución, amarillo listo, rojo bloqueado, gris terminado, azul nuevo). */
    static Color fuerte(EstadoProceso estado) {
        return switch (estado) {
            case EJECUCION -> VERDE;
            case LISTO -> AMARILLO;
            case BLOQUEADO -> ROJO;
            case TERMINADO -> GRIS;
            case NUEVO -> AZUL;
        };
    }

    /** Versión muy clara del color del estado, para el fondo de las filas. */
    static Color suave(EstadoProceso estado) {
        return mezclar(fuerte(estado), Color.WHITE, 0.88);
    }

    /** Mezcla dos colores; peso es la parte del segundo (0 = solo base, 1 = solo mezcla). */
    static Color mezclar(Color base, Color otro, double peso) {
        int r = (int) Math.round(base.getRed() * (1 - peso) + otro.getRed() * peso);
        int g = (int) Math.round(base.getGreen() * (1 - peso) + otro.getGreen() * peso);
        int b = (int) Math.round(base.getBlue() * (1 - peso) + otro.getBlue() * peso);
        return new Color(r, g, b);
    }

    /** Color de la RAM según su uso: verde holgado, amarillo justo, rojo casi llena. */
    static Color ram(double uso) {
        return uso < 0.6 ? VERDE : uso < 0.85 ? AMARILLO : ROJO;
    }
}

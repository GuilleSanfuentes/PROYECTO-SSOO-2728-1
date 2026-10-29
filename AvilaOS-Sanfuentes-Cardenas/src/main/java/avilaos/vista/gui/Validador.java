package avilaos.vista.gui;

/** Valida tipo y rango de los campos de la interfaz y explica el motivo exacto cuando fallan. */
public final class Validador {

    /** Resultado de validar un entero: si es válido trae el valor, si no trae el motivo. */
    public record ResultadoEntero(boolean valido, int valor, String motivo) {

        static ResultadoEntero ok(int valor) {
            return new ResultadoEntero(true, valor, "");
        }

        static ResultadoEntero error(String motivo) {
            return new ResultadoEntero(false, 0, motivo);
        }
    }

    private Validador() {
    }

    /** Valida que el texto sea un entero dentro de [minimo, maximo]. */
    public static ResultadoEntero entero(String texto, String campo, int minimo, int maximo) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            return ResultadoEntero.error(campo + ": está vacío, escribe un número entero entre " + minimo + " y " + maximo);
        }
        long valor;
        try {
            valor = Long.parseLong(limpio);
        } catch (NumberFormatException e) {
            return ResultadoEntero.error(campo + ": '" + limpio + "' no es un número entero");
        }
        if (valor < minimo || valor > maximo) {
            return ResultadoEntero.error(campo + ": " + valor + " está fuera del rango " + minimo + ".." + maximo);
        }
        return ResultadoEntero.ok((int) valor);
    }
}

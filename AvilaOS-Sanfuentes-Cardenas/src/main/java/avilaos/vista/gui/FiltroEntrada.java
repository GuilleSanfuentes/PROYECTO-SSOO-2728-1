package avilaos.vista.gui;

import javax.swing.UIManager;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/** Filtro de escritura: el campo solo acepta los caracteres válidos y hasta un largo máximo; lo demás se descarta con un aviso. */
public final class FiltroEntrada extends DocumentFilter {

    /** Qué caracteres acepta el campo. */
    public enum Tipo {
        DIGITOS,
        NOMBRE,
        IDENTIFICADOR
    }

    public static final int LARGO_NOMBRE = 24;
    public static final int LARGO_IDENTIFICADOR = 12;

    private final JTextComponent campo;
    private final Tipo tipo;
    private final int largoMaximo;

    private FiltroEntrada(JTextComponent campo, Tipo tipo, int largoMaximo) {
        this.campo = campo;
        this.tipo = tipo;
        this.largoMaximo = largoMaximo;
    }

    /** El campo solo acepta dígitos (0-9), como máximo la cantidad indicada. */
    public static void soloDigitos(JTextComponent campo, int maxDigitos) {
        instalar(campo, Tipo.DIGITOS, maxDigitos);
    }

    /** El campo acepta letras, números, espacios, guion y guion bajo, hasta LARGO_NOMBRE caracteres. */
    public static void nombre(JTextComponent campo) {
        instalar(campo, Tipo.NOMBRE, LARGO_NOMBRE);
    }

    /** El campo acepta letras, números, guion y guion bajo, sin espacios, hasta LARGO_IDENTIFICADOR caracteres. */
    public static void identificador(JTextComponent campo) {
        instalar(campo, Tipo.IDENTIFICADOR, LARGO_IDENTIFICADOR);
    }

    /** Instala el filtro en el documento del campo. */
    public static void instalar(JTextComponent campo, Tipo tipo, int largoMaximo) {
        if (campo.getDocument() instanceof AbstractDocument documento) {
            documento.setDocumentFilter(new FiltroEntrada(campo, tipo, largoMaximo));
        }
    }

    /** Indica si el carácter es válido para el tipo de campo. */
    public static boolean aceptable(char c, Tipo tipo) {
        return switch (tipo) {
            case DIGITOS -> c >= '0' && c <= '9';
            case NOMBRE -> Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_';
            case IDENTIFICADOR -> Character.isLetterOrDigit(c) || c == '-' || c == '_';
        };
    }

    @Override
    public void insertString(FilterBypass fb, int posicion, String texto, AttributeSet atributos) throws BadLocationException {
        replace(fb, posicion, 0, texto, atributos);
    }

    @Override
    public void replace(FilterBypass fb, int posicion, int largo, String texto, AttributeSet atributos)
            throws BadLocationException {
        String limpio = limpiar(texto);
        int espacio = largoMaximo - (fb.getDocument().getLength() - largo);
        if (limpio.length() > espacio) {
            limpio = limpio.substring(0, Math.max(0, espacio));
        }
        if (texto != null && limpio.length() < texto.length()) {
            UIManager.getLookAndFeel().provideErrorFeedback(campo);
        }
        fb.replace(posicion, largo, limpio, atributos);
    }

    /** Quita del texto los caracteres que no acepta el campo. */
    private String limpiar(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            if (aceptable(texto.charAt(i), tipo)) {
                sb.append(texto.charAt(i));
            }
        }
        return sb.toString();
    }
}

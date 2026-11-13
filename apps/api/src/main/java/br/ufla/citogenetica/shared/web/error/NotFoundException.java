package br.ufla.citogenetica.shared.web.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(Class<?> clazz, String id) {
        super(clazz.getSimpleName() + " não encontrado com o id: " + id);
    }

    public NotFoundException(Class<?> clazz, Long id) {
        super(clazz.getSimpleName() + " não encontrado com o id: " + id.toString());
    }

    public NotFoundException(String message) {
        super(message);
    }

}

package org.acme.foodpackaging.exception.service;

/**
 * Оборачивает сбой Jackson-сериализации ответа /schedule/frontData.
 *
 * Такой сбой RESTEasy перехватывает на уровне записи ответа, в обход
 * обычной цепочки ExceptionMapper-ов - клиент в этом случае получает
 * только голый текст "Not able to deserialize data provided." без каких-
 * либо деталей. Чтобы вместо этого получать структурированную ошибку
 * (как у остальных эндпоинтов) и полный стектрейс в логах, ScheduleQueryResource
 * сериализует ответ сам, перехватывает сбой здесь и оборачивает его в это
 * исключение - тогда оно доходит до GlobalExceptionHandler как обычно.
 */
public class FrontDataSerializationException extends PackagingException {

    private static final int STATUS = 500;

    public FrontDataSerializationException(String message, Throwable cause) {
        super(message, STATUS, cause);
    }
}

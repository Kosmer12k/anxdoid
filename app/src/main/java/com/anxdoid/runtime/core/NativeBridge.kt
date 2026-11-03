package com.anxdoid.runtime.core

object NativeBridge {
    init {
        // Загрузка нативной C++ библиотеки рантайма
        System.loadLibrary("anxdoid_runtime")
    }

    /**
     * Инициализирует и запускает гостевой Android рантайм.
     * @param firmwarePath Абсолютный путь к скопированному файлу system.img
     * @return 0 при успешном запуске, либо код ошибки
     */
    external fun initializeRuntime(firmwarePath: String): Int
}

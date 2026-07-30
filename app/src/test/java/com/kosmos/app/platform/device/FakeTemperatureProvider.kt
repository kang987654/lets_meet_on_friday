package com.kosmos.app.platform.device

/**
 * [FakeTemperatureProvider]
 * 테스트 시 온도를 모킹하기 위한 Test Fixture 클래스입니다.
 */
class FakeTemperatureProvider : TemperatureProvider {
    var mockedTemperature: Float = 35.0f

    override fun getCurrentTemperatureCelsius(): Float {
        return mockedTemperature
    }
}

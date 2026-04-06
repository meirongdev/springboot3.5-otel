package contracts.greeting

org.springframework.cloud.contract.spec.Contract.make {
    name "should_get_japanese_greeting"
    description "Represents a successful response for a Japanese greeting"

    request {
        method 'GET'
        url '/api/greetings'
        headers {
            header('Accept-Language', 'ja')
        }
    }

    response {
        status OK()
        headers {
            header('Content-Type': 'application/json')
        }
        body([
            language: 'ja',
            message: 'こんにちは世界！'
        ])
    }
}

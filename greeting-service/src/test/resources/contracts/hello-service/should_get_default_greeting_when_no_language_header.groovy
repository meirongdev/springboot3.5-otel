package contracts.greeting

org.springframework.cloud.contract.spec.Contract.make {
    name "should_get_default_greeting_when_no_language_header"
    description "Returns default English greeting when no Accept-Language header is provided"
    priority 100  // Lower priority than specific language stubs

    request {
        method 'GET'
        url '/api/greetings'
    }

    response {
        status 200
        headers {
            header('Content-Type': 'application/json')
        }
        body([
            language: 'en',
            message: 'Hello, World!'
        ])
    }
}

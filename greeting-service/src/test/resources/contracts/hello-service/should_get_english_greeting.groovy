package contracts.greeting

org.springframework.cloud.contract.spec.Contract.make {
    name "should_get_english_greeting"
    description "Represents a successful response for an English greeting"
    
    request {
        method 'GET'
        url '/api/greetings'
        headers {
            header('Accept-Language': 'en')
        }
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

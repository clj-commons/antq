.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

.PHONY: outdated
outdated: ## Run antq to detect outdated dependencies
	clojure -M:outdated:nop --upgrade

.PHONY: test
test: install ## Run tests
	clojure -M:dev:1.10:test
	clojure -M:dev:1.11:test
	clojure -M:dev:test
	script/integration_test.sh

.PHONY: lint
lint: ## Run linters
	cljstyle check
	clj-kondo --lint src:test

.PHONY: uberjar
uberjar: clean ## Generate uberjar file
	clojure -T:build uberjar

.PHONY: jar
jar: clean ## Generate jar file
	clojure -T:build jar

.PHONY: install
install: clean ## Install this library to a local maven repository
	clojure -T:build install

.PHONY: docker
docker: ## Build docker image
	docker build -t uochan/antq .
.PHONY: docker-test
docker-test: ## Run test in a docker container
	docker run --rm -v $(shell pwd):/src -w /src uochan/antq:latest

.PHONY: coverage
coverage: ## Check coverage
	clojure -M:test:coverage:dev:nop --src-ns-path=src --test-ns-path=test --codecov

.PHONY: clean
clean:
	rm -rf .cpcache target

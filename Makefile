.PHONY: help
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

.PHONY: outdated
outdated: ## Run antq to detect outdated dependencies
	clojure -M:outdated:nop --upgrade

.PHONY: test
test: ## Run unit tests
	clojure -M:dev:1.11:test --skip-meta integration
	clojure -M:dev:test --skip-meta integration

.PHONY: test-integration
test-integration: ## Run integration tests
	clojure -M:dev:test --focus-meta integration

.PHONY: lint
lint: ## Run linters
	bb lint-cljstyle check
	bb lint-kondo

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
	clojure -M:dev:test --skip-meta integration --plugin cloverage --codecov --cov-ns-exclude-regex leiningen.antq

.PHONY: clean
clean:
	rm -rf .cpcache target

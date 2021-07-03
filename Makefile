.PHONY: test examples controls acceptance verify results jar clean help

help:
	@printf '%s\n' 'make test        Unit, generated, integration, and regression tests' 'make examples    Run every EDN study and strictly replay its bundle' 'make controls    Execute semantic and artifact corruption controls' 'make acceptance  Freeze source, run the complete campaign, write evidence' 'make verify      Validate a previously generated acceptance bundle' 'make jar         Build target/hazel.jar with its pinned runtime' 'make clean       Remove build products and local experiment outputs'

test:
	bin/clj -m hazel.test-runner

examples:
	bin/clj -m hazel.acceptance examples

controls:
	bin/clj -m hazel.acceptance controls

acceptance:
	bin/clj -m hazel.acceptance run

verify:
	bin/clj -m hazel.acceptance verify

results:
	bin/clj -m hazel.results

jar:
	bin/clj -m hazel.build

clean:
	rm -rf target out

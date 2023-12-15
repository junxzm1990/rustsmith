#!/bin/sh

RUST_GENERATOR_DIR=$1
RUST_CONTRACT_DIR=$2

while true; do

	cd $RUST_GENERATOR_DIR

	# clean the previous batch of Rust programs
	rm -rf $RUST_GENERATOR_DIR/outRust

	#genenrate a new batch of Rust programs
	timeout 20 $RUST_GENERATOR_DIR/rustsmith -n 10

	for prog in $RUST_GENERATOR_DIR/outRust/file*/file*.rs; do
		echo $prog
		mv $prog $RUST_CONTRACT_DIR/src/lib.rs
		cd $RUST_CONTRACT_DIR/
		rm $RUST_CONTRACT_DIR/target/wasm32-unknown-unknown/release/contract.wasm
		cargo build --target wasm32-unknown-unknown --release > /dev/null 2>&1
		if test -e "$RUST_CONTRACT_DIR/target/wasm32-unknown-unknown/release/contract.wasm"; then
			echo "File exists"
		else
			echo "compilation error"
			exit 
		fi
	done

	break
done

#!/bin/sh

RUST_GENERATOR_DIR=$1
RUST_CONTRACT_DIR=$2
NUM_PROG_BATCH=$3
NEAR_DIFF_PROG=$4



export NEAR_DIFF_PROG
export RUST_CONTRACT_DIR


test_rust_func() {
	$NEAR_DIFF_PROG $RUST_CONTRACT_DIR/target/wasm32-unknown-unknown/release/contract.wasm $1
	exit $?
}

export -f test_rust_func

while true; do

	cd $RUST_GENERATOR_DIR

	# clean the previous batch of Rust programs
	rm -rf $RUST_GENERATOR_DIR/outRust

	#genenrate a new batch of Rust programs
	$RUST_GENERATOR_DIR/rustsmith -n $NUM_PROG_BATCH

	for prog in $RUST_GENERATOR_DIR/outRust/file*/file*.rs; do
		echo $prog
		mv $prog $RUST_CONTRACT_DIR/src/lib.rs
		cd $RUST_CONTRACT_DIR/
		rm $RUST_CONTRACT_DIR/target/wasm32-unknown-unknown/release/contract.wasm
		cargo build --target wasm32-unknown-unknown --release > /dev/null 2>&1
		if test -e "$RUST_CONTRACT_DIR/target/wasm32-unknown-unknown/release/contract.wasm"; then
			wasm2wat $RUST_CONTRACT_DIR/target/wasm32-unknown-unknown/release/contract.wasm | grep export | grep fun | grep -o '".*"' | awk '{print substr($0, 2, length($0)-2)}' | parallel --halt now,fail=1 'test_rust_func {} || exit $?'
			if [ $? -ne 0 ]; then
				echo "Diff Detected!!!"
				echo $prog
				exit
			fi
		else
			echo "compilation error"
		fi
	done
done

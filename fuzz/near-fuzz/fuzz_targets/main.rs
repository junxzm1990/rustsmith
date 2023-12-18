#[macro_use]
extern crate afl;

use near_vm_runner::ContractCode;
use near_primitives::types::Balance;
use near_primitives::version::PROTOCOL_VERSION;
use near_vm_runner::precompile_contract;
use near_primitives::runtime::apply_state::ApplyState;
use near_primitives::runtime::config::RuntimeConfig;
use near_store::StoreCompiledContractCache;
use std::sync::Arc;
use near_store::test_utils::create_tries;
use near_primitives::runtime::migration_data::{MigrationData, MigrationFlags};
use std::env;
use std::fs::File;
use std::io::Read;

const GAS_PRICE: Balance = 5000;

fn main() {
    // Get the path to the WASM file from the command line arguments
    let args: Vec<String> = env::args().collect();
    if args.len() != 2 {
        eprintln!("Usage: {} <path-to-wasm-file>", args[0]);
        return;
    }
    let wasm_file_path = &args[1];

    // Load the WASM file
    let mut file = File::open(wasm_file_path).expect("Unable to open the WASM file");
    let mut wasm = Vec::new();
    file.read_to_end(&mut wasm).expect("Unable to read the WASM file");

    // Your existing code, but using `wasm` instead of `data.to_vec()`
    let gas_limit = 10u64.pow(15);
    let tries = create_tries();

    let apply_state = ApplyState {
        block_height: 1,
        prev_block_hash: Default::default(),
        block_hash: Default::default(),
        epoch_id: Default::default(),
        epoch_height: 0,
        gas_price: GAS_PRICE,
        block_timestamp: 100,
        gas_limit: Some(gas_limit),
        random_seed: Default::default(),
        current_protocol_version: PROTOCOL_VERSION,
        config: Arc::new(RuntimeConfig::test()),
        cache: Some(Box::new(StoreCompiledContractCache::new(&tries.get_store()))),
        is_new_chunk: true,
        migration_data: Arc::new(MigrationData::default()),
        migration_flags: MigrationFlags::default(),
    };

    let code = ContractCode::new(wasm, None);
    let vm_kind = apply_state.config.wasm_config.vm_kind; 

    let runtime = vm_kind
        .runtime(apply_state.config.wasm_config.clone())
        .unwrap_or_else(|| panic!("the {vm_kind:?} runtime has not been enabled at compile time"));
   
    runtime.precompile(&code, (apply_state.cache.as_deref()).unwrap());

    //let _result = precompile_contract(&code, &apply_state.config.wasm_config, (&apply_state.cache).as_deref());
}

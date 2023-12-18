#[macro_use]
extern crate afl;

use near_primitives::runtime::config_store::RuntimeConfigStore;
use near_primitives::version::PROTOCOL_VERSION;
use near_vm_runner::logic::errors::FunctionCallError;
use near_vm_runner::logic::mocks::mock_external::MockedExternal;
use near_vm_runner::logic::VMOutcome;
use near_vm_runner::logic::ReturnData;
use near_vm_runner::ContractCode;
use near_vm_runner::VMKind;

use core::fmt;
use near_vm_runner::internal::wasmparser::{Export, ExternalKind, Parser, Payload, TypeDef};
use near_vm_runner::logic::VMContext;
use std::env;
use std::fs::File;
use std::io::Read;



/// Finds a no-parameter exported function, something like `(func (export "entry-point"))`.
pub fn find_entry_point(contract: &ContractCode) -> Option<String> {
/*
    let mut tys = Vec::new();
    let mut fns = Vec::new();
    for payload in Parser::default().parse_all(contract.code()) {
        match payload {
            Ok(Payload::FunctionSection(rdr)) => fns.extend(rdr),
            Ok(Payload::TypeSection(rdr)) => tys.extend(rdr),
            Ok(Payload::ExportSection(rdr)) => {
                for export in rdr {
                    if let Ok(Export { field, kind: ExternalKind::Function, index }) = export {
                        if let Some(&Ok(ty_index)) = fns.get(index as usize) {
                            if let Some(Ok(TypeDef::Func(func_type))) = tys.get(ty_index as usize) {
                                if func_type.params.is_empty() && func_type.returns.is_empty() {
                                    return Some(field.to_string());
                                }
                            }
                        }
                    }
                }
            }
            _ => (),
        }
    }
*/
    None
}

pub fn create_context(input: Vec<u8>) -> VMContext {
    VMContext {
        current_account_id: "alice".parse().unwrap(),
        signer_account_id: "bob".parse().unwrap(),
        signer_account_pk: vec![0, 1, 2, 3, 4],
        predecessor_account_id: "carol".parse().unwrap(),
        input,
        block_height: 10,
        block_timestamp: 42,
        epoch_height: 1,
        account_balance: 2u128,
        account_locked_balance: 0,
        storage_usage: 12,
        attached_deposit: 2u128,
        prepaid_gas: 10_u64.pow(14),
        random_seed: vec![0, 1, 2],
        view_config: None,
        output_data_receivers: vec![],
    }
}

/// Define a configuration for which [`available_imports`] is implemented. This
/// allows to specify the imports available in a [`ConfiguredModule`].
///
/// [`available_imports`]: wasm_smith::Config::available_imports
/// [`ConfiguredModule`]: wasm_smith::ConfiguredModule
#[derive(arbitrary::Arbitrary, Debug)]
pub struct ModuleConfig {}

impl wasm_smith::Config for ModuleConfig {
    /// Returns a WebAssembly module which imports all near host functions. The
    /// imports are grabbed from a compiled [test contract] which calls every
    /// host function in its method `sanity_check`.
    ///
    /// [test contract]: near_test_contracts::rs_contract
    fn available_imports(&self) -> Option<std::borrow::Cow<'_, [u8]>> {
        Some(near_test_contracts::rs_contract().into())
    }
}

/// Wrapper to get more useful Debug.
pub struct ArbitraryModule(pub wasm_smith::ConfiguredModule<ModuleConfig>);

impl<'a> arbitrary::Arbitrary<'a> for ArbitraryModule {
    fn arbitrary(u: &mut arbitrary::Unstructured<'a>) -> arbitrary::Result<Self> {
        wasm_smith::ConfiguredModule::<ModuleConfig>::arbitrary(u).map(ArbitraryModule)
    }
}

impl fmt::Debug for ArbitraryModule {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let bytes = self.0.module.to_bytes();
        write!(f, "{:?}", bytes)?;
        if let Ok(wat) = wasmprinter::print_bytes(&bytes) {
            write!(f, "\n{}", wat)?;
        }
        Ok(())
    }
}
/*
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
*/



fn run_fuzz1(code: &ContractCode, vm_kind: VMKind, arg: Vec<u8>) -> VMOutcome {
    let mut fake_external = MockedExternal::new();
    let mut context = create_context(arg);
    context.prepaid_gas = 10u64.pow(14);
    let config_store = RuntimeConfigStore::new(None);
    let config = config_store.get_config(PROTOCOL_VERSION);
    let fees = &config.fees;
    let mut wasm_config = config.wasm_config.clone();
    wasm_config.limit_config.contract_prepare_version =
        near_vm_runner::logic::ContractPrepareVersion::V2;

    let promise_results = vec![];
    
    let args: Vec<String> = env::args().collect();

    let method_name = &args[2];
  
    let res = vm_kind.runtime(wasm_config).unwrap().run(
        code,
        &method_name,
        &mut fake_external,
        context,
        fees,
        &promise_results,
        None,
    );

    // Remove the VMError message details as they can differ between runtimes
    // TODO: maybe there's actually things we could check for equality here too?
   
    res.unwrap()

    /*
    match res {
        Ok(mut outcome) => {
            if outcome.aborted.is_some() {
                outcome.logs = vec!["[censored]".to_owned()];
                outcome.aborted =
                    Some(FunctionCallError::LinkError { msg: "[censored]".to_owned() });
            }
            outcome
        }
        Err(err) => panic!("fatal error: {err:?}"),
    }
    */
}




fn run_fuzz(code: &ContractCode, vm_kind: VMKind) -> VMOutcome {
    let mut fake_external = MockedExternal::new();
    let mut context = create_context(vec![]);
    context.prepaid_gas = 10u64.pow(14);
    let config_store = RuntimeConfigStore::new(None);
    let config = config_store.get_config(PROTOCOL_VERSION);
    let fees = &config.fees;
    let mut wasm_config = config.wasm_config.clone();
    wasm_config.limit_config.contract_prepare_version =
        near_vm_runner::logic::ContractPrepareVersion::V2;

    let promise_results = vec![];
    
    let args: Vec<String> = env::args().collect();

    let method_name = &args[2];
   

    let res = near_vm_runner::run(
        code,
        &method_name,
        &mut fake_external,
        context,
        &wasm_config,
        fees,
        &promise_results,
        None,
    );

    res.unwrap()
   

/*    
    let res = vm_kind.runtime(wasm_config).unwrap().run(
        code,
        &method_name,
        &mut fake_external,
        context,
        fees,
        &promise_results,
        None,
    );

    // Remove the VMError message details as they can differ between runtimes
    // TODO: maybe there's actually things we could check for equality here too?
    match res {
        Ok(mut outcome) => {
            if outcome.aborted.is_some() {
                outcome.logs = vec!["[censored]".to_owned()];
                outcome.aborted =
                    Some(FunctionCallError::LinkError { msg: "[censored]".to_owned() });
            }
            outcome
        }
        Err(err) => panic!("fatal error: {err:?}"),
    }
    */
}

fn main() {
    // Get the path to the WASM file from the command line arguments
    let args: Vec<String> = env::args().collect();
    if args.len() != 3 && args.len() != 4 {
        eprintln!("Usage: {} <path-to-wasm-file> <function-name> <optional: argument>", args[0]);
        return;
    }
    let wasm_file_path = &args[1];

    // Load the WASM file
    let mut file = File::open(wasm_file_path).expect("Unable to open the WASM file");
    let mut wasm = Vec::new();
    file.read_to_end(&mut wasm).expect("Unable to read the WASM file");

    let code = ContractCode::new(wasm, None);
    let wasmtime = run_fuzz1(&code, VMKind::Wasmer0, args[3].as_bytes().to_vec());
    let near_vm = run_fuzz1(&code, VMKind::NearVm, args[3].as_bytes().to_vec());

    assert_eq!(near_vm, wasmtime);
    /*
    assert!(false);
    let result = match near_vm.return_data {
                ReturnData::Value(buf) => buf,
                ReturnData::ReceiptIndex(_) | ReturnData::None => vec![],
            };
    */
    println!("{:?}", near_vm);
    println!("{:?}", wasmtime);
    
    // let result = match wasmtime.return_data {
    //            ReturnData::Value(buf) => buf,
    //           ReturnData::ReceiptIndex(_) | ReturnData::None => vec![],
    //        };

    //println!("{:?}", result);

    //let _result = precompile_contract(&code, &apply_state.config.wasm_config, (&apply_state.cache).as_deref());
}

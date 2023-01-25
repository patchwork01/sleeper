/*
 * Copyright 2022-2023 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::{fs, path::Path, sync::Arc};

use parquet::{
    data_type::Int32Type,
    file::{
        properties::WriterProperties,
        reader::{FileReader, SerializedFileReader},
        writer::SerializedFileWriter,
    },
    schema::parser::parse_message_type,
};
use parquet::column::reader::ColumnReader;

fn main() {
    let path = Path::new("../test.parquet");

    write_ints_file(path, &[1, 2, 3]);

    let bytes = fs::read(&path).unwrap();
    assert_eq!(&bytes[0..4], &[b'P', b'A', b'R', b'1']);

    let values = read_ints_file(path);
    assert_eq!(values, vec![1, 2, 3]);
}

fn write_ints_file(path: &Path, values: &[i32]) {
    let message_type = "
  message schema {
    REQUIRED INT32 b;
  }
";
    let schema = Arc::new(parse_message_type(message_type).unwrap());
    let props = Arc::new(WriterProperties::builder().build());
    let file = fs::File::create(&path).unwrap();
    let mut writer = SerializedFileWriter::new(file, schema, props).unwrap();
    let mut row_group_writer = writer.next_row_group().unwrap();
    while let Some(mut col_writer) = row_group_writer.next_column().unwrap() {
        col_writer.typed::<Int32Type>().write_batch(values, None, None).unwrap();
        col_writer.close().unwrap();
    }
    row_group_writer.close().unwrap();
    writer.close().unwrap();
}

fn read_ints_file(path: &Path) -> Vec<i32> {

    // Reading data using column reader API.

    let file = fs::File::open(path).unwrap();
    let reader = SerializedFileReader::new(file).unwrap();
    let metadata = reader.metadata();

    let mut res = Ok((0, 0));
    let mut values = vec![0; 8];

    for i in 0..metadata.num_row_groups() {
        let row_group_reader = reader.get_row_group(i).unwrap();
        let row_group_metadata = metadata.row_group(i);
        println!("Found a row group");

        for j in 0..row_group_metadata.num_columns() {
            println!("Found a column");
            let mut column_reader = row_group_reader.get_column_reader(j).unwrap();
            match column_reader {
                // You can also use `get_typed_column_reader` method to extract typed reader.
                ColumnReader::Int32ColumnReader(ref mut typed_reader) => {
                    res = typed_reader.read_batch(
                        8, // batch size
                        None,
                        None,
                        &mut values,
                    );
                }
                _ => {}
            }
        }
    }

    values.truncate(res.unwrap().0);
    return values;
}

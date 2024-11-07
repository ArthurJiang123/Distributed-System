import re
import csv
import os

time_pattern = re.compile(r'Time Taken: (\d+) ms')

# Loop through player_1.log to player_9.log
for i in range(1, 10):
    # Define input and output file names
    input_file = f'player_{i}.log'
    output_file = f'time_taken_player_{i}.csv'

    time_values = []

    if not os.path.isfile(input_file):
        print(f"{input_file} does not exist. Skipping.")
        continue

    # Read and extract time values
    with open(input_file, 'r') as file:
        for line in file:
            match = time_pattern.search(line)
            if match:
                time_values.append(int(match.group(1)))

    # Write to a CSV file
    with open(output_file, 'w', newline='') as csvfile:
        csv_writer = csv.writer(csvfile)
        csv_writer.writerow(['Time Taken (ms)'])  # Header row
        for time in time_values:
            csv_writer.writerow([time])

    print(f"Time values extracted and saved to {output_file}")